package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.application.admin.AdminMatchesUseCase
import kotlinx.serialization.json.JsonElement
import io.ktor.server.routing.put
import io.ktor.server.request.receive
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.admin.AdminActionsUseCase
import com.kglabs28.yapnplay.backend.application.settings.SettingsService
import org.slf4j.LoggerFactory
import kotlinx.serialization.Serializable
import io.ktor.server.routing.post
import com.kglabs28.yapnplay.backend.infrastructure.monitoring.ErrorTracking
import com.kglabs28.yapnplay.backend.api.auth.AdminTokenCheck
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.application.admin.GetAdminStatsUseCase
import com.kglabs28.yapnplay.backend.application.admin.ServerUptime
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import java.lang.management.ManagementFactory

/**
 * Operator-only dashboard. The page itself holds no data and is public, as
 * is the server's start time; everything else needs the admin token. Every
 * change (settings, blocks, reports, coins) names who made it and is audited.
 */
fun Route.adminRoutes(
    getStats: GetAdminStatsUseCase,
    tokenCheck: AdminTokenCheck,
    settings: SettingsService,
    actions: AdminActionsUseCase,
    adminMatches: AdminMatchesUseCase,
) {
    val page by lazy {
        AdminTokenCheck::class.java.getResource(DASHBOARD_RESOURCE)?.readText() ?: error(DASHBOARD_RESOURCE)
    }
    get(HttpPaths.ADMIN_PAGE) {
        call.noStore()
        call.response.header(FRAME_OPTIONS, DENY)
        call.respondText(page, ContentType.Text.Html)
    }
    get(HttpPaths.ADMIN_UPTIME) {
        call.noStore()
        call.respond(ServerUptime(ManagementFactory.getRuntimeMXBean().startTime, System.currentTimeMillis()))
    }
    get(HttpPaths.ADMIN_STATS) {
        call.noStore()
        if (!tokenCheck.allows(call)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED))
            return@get
        }
        call.respond(getStats())
    }
    // Sends one test error to Sentry so the setup can be checked.
    post(HttpPaths.ADMIN_TEST_ERROR) {
        call.noStore()
        if (!tokenCheck.allows(call)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorBody(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED))
            return@post
        }
        if (ErrorTracking.enabled) {
            LoggerFactory.getLogger(TEST_ERROR_LOGGER).error(TEST_ERROR_MESSAGE, IllegalStateException(TEST_ERROR_MESSAGE))
            ErrorTracking.flush()
        }
        call.respond(TestErrorResult(sentryEnabled = ErrorTracking.enabled))
    }
    get(HttpPaths.ADMIN_SETTINGS) { call.admin(tokenCheck) { call.respond(settings.list()) } }
    put(HttpPaths.ADMIN_SETTING) {
        call.admin(tokenCheck) {
            val body = call.receive<AdminSettingBody>()
            call.respond(settings.update(call.parameters[HttpPaths.KEY_PARAM].orEmpty(), body.value, body.by.requireName(), body.note))
        }
    }
    get(HttpPaths.ADMIN_USERS) { call.admin(tokenCheck) { call.respond(actions.users(call.request.queryParameters[HttpPaths.QUERY_PARAM].orEmpty())) } }
    post(HttpPaths.ADMIN_USER_BLOCK) {
        call.admin(tokenCheck) {
            val body = call.receive<AdminBlockBody>()
            actions.block(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), body.days, body.by.requireName(), body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post(HttpPaths.ADMIN_USER_UNBLOCK) {
        call.admin(tokenCheck) {
            val body = call.receive<AdminNoteBody>()
            actions.unblock(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), body.by.requireName(), body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    post(HttpPaths.ADMIN_USER_COINS) {
        call.admin(tokenCheck) {
            val body = call.receive<AdminCoinsBody>()
            val balance = actions.adjustCoins(call.parameters[HttpPaths.USER_ID_PARAM].orEmpty(), body.delta, body.by.requireName(), body.note)
            call.respond(AdminCoinsResult(balance))
        }
    }
    get(HttpPaths.ADMIN_REPORTS) { call.admin(tokenCheck) { call.respond(actions.reports()) } }
    post(HttpPaths.ADMIN_REPORT_DECIDE) {
        call.admin(tokenCheck) {
            val body = call.receive<AdminReportBody>()
            actions.decideReport(call.parameters[HttpPaths.REPORT_ID_PARAM].orEmpty(), body.upheld, body.by.requireName(), body.note)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get(HttpPaths.ADMIN_AUDIT) { call.admin(tokenCheck) { call.respond(actions.auditLog()) } }
    get(HttpPaths.ADMIN_MATCHES) { call.admin(tokenCheck) { call.respond(adminMatches.live()) } }
    get(HttpPaths.ADMIN_MATCH_BOT) {
        call.admin(tokenCheck) { call.respond(adminMatches.botDecisions(call.parameters[HttpPaths.MATCH_ID_PARAM].orEmpty())) }
    }
}

/** Runs [block] only with the admin token; otherwise 401. Never cached. */
private suspend fun RoutingCall.admin(tokenCheck: AdminTokenCheck, block: suspend () -> Unit) {
    noStore()
    if (!tokenCheck.allows(this)) {
        respond(HttpStatusCode.Unauthorized, ErrorBody(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED))
        return
    }
    block()
}

/** Who made the change (typed on the dashboard), for the audit. */
private fun String.requireName(): String =
    trim().takeIf { it.isNotEmpty() } ?: throw ServiceException(ErrorCode.BAD_REQUEST, "Type your name so the change can be audited")

@Serializable
data class AdminSettingBody(val value: JsonElement, val by: String, val note: String? = null)

@Serializable
data class AdminBlockBody(val days: Int, val by: String, val note: String? = null)

@Serializable
data class AdminNoteBody(val by: String, val note: String? = null)

@Serializable
data class AdminCoinsBody(val delta: Int, val by: String, val note: String? = null)

@Serializable
data class AdminCoinsResult(val coins: Int)

@Serializable
data class AdminReportBody(val upheld: Boolean, val by: String, val note: String? = null)

/** Reply of the test-error route: false when SENTRY_DSN is not set. */
@Serializable
data class TestErrorResult(val sentryEnabled: Boolean)

private fun RoutingCall.noStore() = response.header(HttpHeaders.CacheControl, NO_STORE)

private const val DASHBOARD_RESOURCE = "/admin/dashboard.html"
private const val NO_STORE = "no-store"
private const val FRAME_OPTIONS = "X-Frame-Options"
private const val DENY = "DENY"
private const val TEST_ERROR_LOGGER = "AdminTestError"
private const val TEST_ERROR_MESSAGE = "Test error from the YapNPlay admin: Sentry is working"
