package com.kglabs28.yapnplay.backend.api.http

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
 * Operator-only, read-only dashboard. The page itself holds no data and
 * is public, as is the server's start time; every number comes from
 * [HttpPaths.ADMIN_STATS], which needs the admin token.
 */
fun Route.adminRoutes(getStats: GetAdminStatsUseCase, tokenCheck: AdminTokenCheck) {
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
}

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
