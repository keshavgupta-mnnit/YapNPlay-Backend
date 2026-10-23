package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.contract.DeviceAccountBody
import com.kglabs28.yapnplay.backend.application.identity.FindDeviceAccountUseCase
import com.kglabs28.yapnplay.backend.contract.CreateGuestBody
import io.ktor.server.request.receiveNullable
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
fun Route.publicRoutes(createGuest: CreateGuestUseCase, playGames: PlayGamesSignInUseCase, findDevice: FindDeviceAccountUseCase, guestLimit: RateLimitName) {
    get(HttpPaths.HEALTH) {
        call.respond(HealthResponse(BackendStrings.STATUS_OK, CONTRACT_VERSION, BuildInfo.version))
    }
    rateLimit(guestLimit) {
        post(HttpPaths.GUEST) {
            // Older apps send no body: the server picks the account then.
            val request = runCatching { call.receiveNullable<CreateGuestBody>() }.getOrNull()
            call.respond(HttpStatusCode.Created, createGuest(request))
        }
        // The account already made on this phone, if any (offered back to the player first).
        post(HttpPaths.DEVICE_ACCOUNT) {
            call.respond(findDevice(call.receive<DeviceAccountBody>().deviceId))
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
