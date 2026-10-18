package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.contract.transport.CONTRACT_VERSION
import com.kglabs28.yapnplay.backend.application.identity.CreateGuestUseCase
import com.kglabs28.yapnplay.backend.config.BackendStrings
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Routes that need no sign-in: health check and guest creation. */
fun Route.publicRoutes(createGuest: CreateGuestUseCase, guestLimit: RateLimitName) {
    get(HttpPaths.HEALTH) {
        call.respond(HealthResponse(BackendStrings.STATUS_OK, CONTRACT_VERSION))
    }
    rateLimit(guestLimit) {
        post(HttpPaths.GUEST) {
            call.respond(HttpStatusCode.Created, createGuest())
        }
    }
}
