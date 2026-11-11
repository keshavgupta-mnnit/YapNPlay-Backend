package com.kglabs28.yapnplay.backend.plugins

import com.kglabs28.yapnplay.backend.api.auth.BearerUserAuthProvider
import com.kglabs28.yapnplay.backend.application.identity.AuthenticateUseCase
import com.kglabs28.yapnplay.backend.application.identity.WsTicketIssuer
import com.kglabs28.yapnplay.backend.config.BackendConstants
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

/** Name of the per-address limit on guest creation. */
val GUEST_RATE_LIMIT = RateLimitName("guest-creation")
val PLAYER_RATE_LIMIT = RateLimitName("player-calls")
/** Web load reports, per address (a page sends a handful; this only stops floods). */
val WEB_LOG_RATE_LIMIT = RateLimitName("web-logs")

/** Bearer sign-in for every protected route, and the guest-creation rate limit. */
fun Application.configureSecurity(authenticate: AuthenticateUseCase, tickets: WsTicketIssuer, devMode: Boolean, guestCreationsPerHour: Int) {
    install(Authentication) {
        register(BearerUserAuthProvider(BearerUserAuthProvider.Config(BearerUserAuthProvider.NAME, authenticate, devMode, tickets)))
    }
    install(RateLimit) {
        register(GUEST_RATE_LIMIT) {
            rateLimiter(limit = guestCreationsPerHour, refillPeriod = BackendConstants.SECONDS_PER_HOUR.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
        register(WEB_LOG_RATE_LIMIT) {
            rateLimiter(limit = WEB_LOGS_PER_HOUR, refillPeriod = BackendConstants.SECONDS_PER_HOUR.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
        // Every signed-in HTTP call, per address: stops scripts hammering the server.
        register(PLAYER_RATE_LIMIT) {
            rateLimiter(limit = BackendConstants.HTTP_CALLS_PER_MINUTE, refillPeriod = BackendConstants.SECONDS_PER_MINUTE.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}

private const val WEB_LOGS_PER_HOUR = 60
