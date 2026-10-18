package com.kglabs28.yapnplay.backend.plugins

import com.kglabs28.yapnplay.backend.api.auth.BearerUserAuthProvider
import com.kglabs28.yapnplay.backend.application.identity.AuthenticateUseCase
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

/** Bearer sign-in for every protected route, and the guest-creation rate limit. */
fun Application.configureSecurity(authenticate: AuthenticateUseCase, devMode: Boolean, guestCreationsPerHour: Int) {
    install(Authentication) {
        register(BearerUserAuthProvider(BearerUserAuthProvider.Config(BearerUserAuthProvider.NAME, authenticate, devMode)))
    }
    install(RateLimit) {
        register(GUEST_RATE_LIMIT) {
            rateLimiter(limit = guestCreationsPerHour, refillPeriod = BackendConstants.SECONDS_PER_HOUR.seconds)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }
}
