package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.domain.admin.AdminUserSort
import com.kglabs28.yapnplay.backend.domain.admin.AdminUserDate
import com.kglabs28.yapnplay.backend.domain.admin.AdminUserFilter
import com.kglabs28.yapnplay.backend.application.admin.AdminFeedbackReplyBody
import com.kglabs28.yapnplay.backend.application.admin.AdminFeedbackUseCase
import com.kglabs28.yapnplay.backend.api.auth.AdminAuth
import com.kglabs28.yapnplay.backend.api.auth.AdminTokenCheck
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.application.admin.AdminActionsUseCase
import com.kglabs28.yapnplay.backend.application.admin.AdminAuthUseCase
import com.kglabs28.yapnplay.backend.application.admin.AdminMatchesUseCase
import com.kglabs28.yapnplay.backend.application.admin.AdminRemovalUseCase
import com.kglabs28.yapnplay.backend.application.admin.GetAdminStatsUseCase
import com.kglabs28.yapnplay.backend.application.admin.ServerUptime
import com.kglabs28.yapnplay.backend.application.admin.StatsRange
import com.kglabs28.yapnplay.backend.application.admin.view
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.settings.SettingsService
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccount
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.admin.AdminPermission
import com.kglabs28.yapnplay.backend.domain.admin.AdminPermissions
import com.kglabs28.yapnplay.backend.infrastructure.monitoring.ErrorTracking
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory

/**
 * Operator-only dashboard. The page itself holds no data and is public, as
 * are the server's start time and the sign-in; everything else needs a
 * signed-in admin (or the server's ADMIN_TOKEN) with the route's permission.
 * Every change is audited under the signed-in admin's ID, and so is every
 * refused attempt.
 */
fun Route.adminRoutes(
    getStats: GetAdminStatsUseCase,
    adminAuth: AdminAuth,
    auth: AdminAuthUseCase,
    settings: SettingsService,
    actions: AdminActionsUseCase,
    removal: AdminRemovalUseCase,
    adminMatches: AdminMatchesUseCase,
    audit: AdminAuditRepository,
    feedback: AdminFeedbackUseCase,
) {
    val page by lazy {
        AdminTokenCheck::class.java.getResource(DASHBOARD_RESOURCE)?.readText() ?: error(DASHBOARD_RESOURCE)
    }
    val guard = AdminGuard(adminAuth, audit)
    get(HttpPaths.ADMIN_PAGE) {
        call.noStore()
        call.response.header(FRAME_OPTIONS, DENY)
        call.respondText(page, ContentType.Text.Html)
    }
    get(HttpPaths.ADMIN_UPTIME) {
        call.noStore()
        call.respond(ServerUptime(ManagementFactory.getRuntimeMXBean().startTime, System.currentTimeMillis()))
    }
    post(HttpPaths.ADMIN_LOGIN) {
        call.noStore()
        val body = call.receive<AdminLoginBody>()
        call.respond(auth.login(body.adminId, body.password))
    }
    post(HttpPaths.ADMIN_LOGOUT) {
        call.noStore()
        AdminAuth.bearer(call)?.let { auth.logout(it) }
        call.respond(HttpStatusCode.NoContent)
    }
    get(HttpPaths.ADMIN_ME) { guard.run(call, AdminPermission.VIEW) { call.respond(it.view()) } }
    post(HttpPaths.ADMIN_PASSWORD) {
        guard.run(call, AdminPermission.VIEW) { admin ->
            val body = call.receive<AdminPasswordBody>()
            if (admin.adminId == AdminAuth.TOKEN_ADMIN) throw ServiceException(ErrorCode.BAD_REQUEST, "The ADMIN_TOKEN has no password to change")
            auth.changePassword(admin, AdminAuth.bearer(call), body.currentPassword, body.newPassword)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get(HttpPaths.ADMIN_ADMINS) { guard.run(call, AdminPermission.CREATE_ADMINS) { call.respond(auth.admins()) } }
    post(HttpPaths.ADMIN_ADMINS) {
        guard.run(call, AdminPermission.CREATE_ADMINS) { admin ->
            val body = call.receive<AdminCreateBody>()
            val permissions = AdminPermissions(body.readOnly, body.editSettings, body.removePlayers, body.createAdmins)
            call.respond(auth.createAdmin(admin, body.adminId, body.password, permissions))
        }
    }
    // `from` / `to` (epoch millis, `to` left out) pick the period; neither: every time.
    get(HttpPaths.ADMIN_STATS) {
        guard.run(call, AdminPermission.VIEW) {
            val params = call.request.queryParameters
            call.respond(getStats(StatsRange.of(params[FROM_PARAM]?.toLongOrNull(), params[TO_PARAM]?.toLongOrNull())))
        }
    }
    // Sends one test error to Sentry so the setup can be checked.
    post(HttpPaths.ADMIN_TEST_ERROR) {
        guard.run(call, AdminPermission.VIEW) {
            if (ErrorTracking.enabled) {
                LoggerFactory.getLogger(TEST_ERROR_LOGGER).error(TEST_ERROR_MESSAGE, IllegalStateException(TEST_ERROR_MESSAGE))
                ErrorTracking.flush()
            }
            call.respond(TestErrorResult(sentryEnabled = ErrorTracking.enabled))
        }
    }
    get(HttpPaths.ADMIN_SETTINGS) { guard.run(call, AdminPermission.VIEW) { call.respond(settings.list()) } }
    put(HttpPaths.ADMIN_SETTING) {
        guard.run(call, AdminPermission.EDIT_SETTINGS) { admin ->
            val body = call.receive<AdminSettingBody>()
            call.respond(settings.update(call.parameters[HttpPaths.KEY_PARAM].orEmpty(), body.value, admin.adminId, body.note))
        }
    }
    get(HttpPaths.ADMIN_USERS) {
        guard.run(call, AdminPermission.VIEW) {
            val params = call.request.queryParameters
            val filter = AdminUserFilter(
                fromMillis = params[FROM_PARAM]?.toLongOrNull(),
                toMillis = params[TO_PARAM]?.toLongOrNull(),
                by = AdminUserDate.entries.firstOrNull { it.name.equals(params[DATE_BY_PARAM], ignoreCase = true) } ?: AdminUserDate.JOINED,
                sort = AdminUserSort.entries.firstOrNull { it.name.equals(params[SORT_PARAM], ignoreCase = true) } ?: AdminUserSort.NEWEST,
            )
            call.respond(actions.users(params[HttpPaths.QUERY_PARAM].orEmpty(), filter))
        }
    }
    post(HttpPaths.ADMIN_USER_BLOCK) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminBlockBody>()
            actions.block(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), body.days, admin.adminId, body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post(HttpPaths.ADMIN_USER_UNBLOCK) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminNoteBody>()
            actions.unblock(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), admin.adminId, body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post(HttpPaths.ADMIN_USER_COINS) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminCoinsBody>()
            val balance = actions.adjustCoins(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), body.delta, admin.adminId, body.note)
            call.respond(AdminCoinsResult(balance))
        }
    }
    delete(HttpPaths.ADMIN_USER) {
        guard.run(call, AdminPermission.REMOVE_PLAYERS) { admin ->
            removal.deleteUser(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), admin.adminId, call.request.queryParameters[NOTE_PARAM])
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get(HttpPaths.ADMIN_ROOMS) { guard.run(call, AdminPermission.VIEW) { call.respond(removal.rooms(call.request.queryParameters[HttpPaths.QUERY_PARAM].orEmpty())) } }
    delete(HttpPaths.ADMIN_ROOM) {
        guard.run(call, AdminPermission.REMOVE_PLAYERS) { admin ->
            removal.deleteRoom(call.parameters[HttpPaths.ROOM_ID_PARAM].orEmpty(), admin.adminId, call.request.queryParameters[NOTE_PARAM])
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get(HttpPaths.ADMIN_REPORTS) { guard.run(call, AdminPermission.VIEW) { call.respond(actions.reports()) } }
    get(HttpPaths.ADMIN_FEEDBACK) { guard.run(call, AdminPermission.VIEW) { call.respond(feedback.list()) } }
    get(HttpPaths.ADMIN_FEEDBACK_TICKET) {
        guard.run(call, AdminPermission.VIEW) { call.respond(feedback.thread(call.parameters[HttpPaths.TICKET_ID_PARAM].orEmpty())) }
    }
    // Replying is moderation work: read-only admins can read but not answer.
    post(HttpPaths.ADMIN_FEEDBACK_REPLY) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminFeedbackReplyBody>()
            call.respond(feedback.reply(call.parameters[HttpPaths.TICKET_ID_PARAM].orEmpty(), admin.adminId, body.message))
        }
    }
    post(HttpPaths.ADMIN_REPORT_DECIDE) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminReportBody>()
            actions.decideReport(call.parameters[HttpPaths.REPORT_ID_PARAM].orEmpty(), body.upheld, admin.adminId, body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post(HttpPaths.ADMIN_REPORT_REVERT) {
        guard.run(call, AdminPermission.MODERATE) { admin ->
            val body = call.receive<AdminNoteBody>()
            actions.revertReport(call.parameters[HttpPaths.REPORT_ID_PARAM].orEmpty(), admin.adminId, body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get(HttpPaths.ADMIN_AUDIT) { guard.run(call, AdminPermission.VIEW) { call.respond(actions.auditLog()) } }
    get(HttpPaths.ADMIN_MATCHES) { guard.run(call, AdminPermission.VIEW) { call.respond(adminMatches.live()) } }
    get(HttpPaths.ADMIN_MATCH_BOT) {
        guard.run(call, AdminPermission.VIEW) { call.respond(adminMatches.botDecisions(call.parameters[HttpPaths.MATCH_ID_PARAM].orEmpty())) }
    }
}

/** Signs the caller in and checks the route's permission: 401 when nobody is signed in, 403 (audited) when not allowed. */
private class AdminGuard(private val adminAuth: AdminAuth, private val audit: AdminAuditRepository) {
    suspend fun run(call: RoutingCall, permission: AdminPermission, block: suspend (AdminAccount) -> Unit) {
        call.noStore()
        val admin = adminAuth.account(call)
        if (admin == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED))
            return
        }
        if (!admin.permissions.allows(permission)) {
            val target = "${call.request.local.method.value} ${call.request.local.uri}".take(TARGET_MAX)
            audit.record(AdminAuditEntry(DENIED, target, null, permission.name, admin.adminId, null, System.currentTimeMillis()))
            call.respond(HttpStatusCode.Forbidden, ErrorBody(ErrorCode.NOT_ADMIN, "Your admin account may not do this (${permission.label()})"))
            return
        }
        block(admin)
    }

    private fun AdminPermission.label() = when (this) {
        AdminPermission.VIEW -> "view"
        AdminPermission.MODERATE -> "read only"
        AdminPermission.EDIT_SETTINGS -> "needs Edit settings"
        AdminPermission.REMOVE_PLAYERS -> "needs Remove players"
        AdminPermission.CREATE_ADMINS -> "needs Create admins"
    }

    private companion object {
        const val DENIED = "DENIED"
        const val TARGET_MAX = 64
    }
}

@Serializable
data class AdminLoginBody(val adminId: String, val password: String)

@Serializable
data class AdminPasswordBody(val currentPassword: String, val newPassword: String)

@Serializable
data class AdminCreateBody(
    val adminId: String,
    val password: String,
    val readOnly: Boolean = false,
    val editSettings: Boolean = false,
    val removePlayers: Boolean = false,
    val createAdmins: Boolean = false,
)

/** [by] is ignored: the signed-in admin is recorded. Kept so older callers still parse. */
@Serializable
data class AdminSettingBody(val value: JsonElement, val by: String = "", val note: String? = null)

@Serializable
data class AdminBlockBody(val days: Int, val by: String = "", val note: String? = null)

@Serializable
data class AdminNoteBody(val by: String = "", val note: String? = null)

@Serializable
data class AdminCoinsBody(val delta: Int, val by: String = "", val note: String? = null)

@Serializable
data class AdminCoinsResult(val coins: Int)

@Serializable
data class AdminReportBody(val upheld: Boolean, val by: String = "", val note: String? = null)

/** Reply of the test-error route: false when SENTRY_DSN is not set. */
@Serializable
data class TestErrorResult(val sentryEnabled: Boolean)

private fun RoutingCall.noStore() = response.header(HttpHeaders.CacheControl, NO_STORE)

private const val DASHBOARD_RESOURCE = "/admin/dashboard.html"
private const val NO_STORE = "no-store"
private const val FRAME_OPTIONS = "X-Frame-Options"
private const val DENY = "DENY"
private const val NOTE_PARAM = "note"
/** /admin/stats period: from and to, epoch millis. */
private const val FROM_PARAM = "from"
private const val TO_PARAM = "to"
private const val DATE_BY_PARAM = "by"
private const val SORT_PARAM = "sort"
private const val TEST_ERROR_LOGGER = "AdminTestError"
private const val TEST_ERROR_MESSAGE = "Test error from the YapNPlay admin: Sentry is working"
