package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BuildInfo
import io.ktor.server.request.receive
import io.ktor.http.HttpHeaders
import com.kglabs28.yapnplay.backend.contract.PlayGamesSignInBody
import com.kglabs28.yapnplay.backend.application.identity.PlayGamesSignInUseCase
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

/** Routes that need no sign-in: health check, guest creation and Play Games sign-in. */
fun Route.publicRoutes(createGuest: CreateGuestUseCase, playGames: PlayGamesSignInUseCase, guestLimit: RateLimitName) {
    get(HttpPaths.HEALTH) {
        call.respond(HealthResponse(BackendStrings.STATUS_OK, CONTRACT_VERSION, BuildInfo.version))
    }
    rateLimit(guestLimit) {
        post(HttpPaths.GUEST) {
            call.respond(HttpStatusCode.Created, createGuest())
        }
        // The bearer is optional here: the app sends its guest's, so a new Play Games player is linked to it.
        post(HttpPaths.PLAY_GAMES_SIGN_IN) {
            val bearer = call.request.headers[HttpHeaders.Authorization]
                ?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
                ?.substring(BEARER_PREFIX.length)?.trim()
            call.respond(playGames(call.receive<PlayGamesSignInBody>().serverAuthCode, bearer))
        }
    }
}

private const val BEARER_PREFIX = "Bearer "
