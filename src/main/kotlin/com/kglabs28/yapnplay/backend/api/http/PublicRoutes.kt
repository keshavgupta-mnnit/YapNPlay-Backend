package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.application.identity.RecordClientUseCase
import com.kglabs28.yapnplay.backend.contract.DeviceAccountBody
import com.kglabs28.yapnplay.backend.application.identity.FindDeviceAccountUseCase
import com.kglabs28.yapnplay.backend.contract.CreateGuestBody
import io.ktor.server.request.receiveNullable
import com.kglabs28.yapnplay.backend.config.BuildInfo
import io.ktor.server.request.receive
import io.ktor.http.HttpHeaders
import com.kglabs28.yapnplay.backend.contract.PlayGamesSignInBody
import com.kglabs28.yapnplay.backend.contract.GoogleSignInBody
import com.kglabs28.yapnplay.backend.application.identity.GoogleSignInUseCase
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

/** Routes that need no sign-in: health check, guest creation, Play Games and Google (web) sign-in. */
fun Route.publicRoutes(
    createGuest: CreateGuestUseCase,
    playGames: PlayGamesSignInUseCase,
    google: GoogleSignInUseCase,
    findDevice: FindDeviceAccountUseCase,
    recordClient: RecordClientUseCase,
    guestLimit: RateLimitName,
) {
    get(HttpPaths.HEALTH) {
        call.respond(HealthResponse(BackendStrings.STATUS_OK, CONTRACT_VERSION, BuildInfo.version))
    }
    rateLimit(guestLimit) {
        post(HttpPaths.GUEST) {
            // Older apps send no body: the server picks the account then.
            val request = runCatching { call.receiveNullable<CreateGuestBody>() }.getOrNull()
            val created = createGuest(request)
            recordClient(created.userId, call.request.headers[HttpHeaders.UserAgent])
            call.respond(HttpStatusCode.Created, created)
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
            val reply = playGames(call.receive<PlayGamesSignInBody>().serverAuthCode, bearer)
            recordClient(reply.userId, call.request.headers[HttpHeaders.UserAgent])
            call.respond(reply)
        }
        // The web app's only way in (it never makes a guest).
        post(HttpPaths.GOOGLE_SIGN_IN) {
            val reply = google(call.receive<GoogleSignInBody>().idToken)
            recordClient(reply.userId, call.request.headers[HttpHeaders.UserAgent])
            call.respond(reply)
        }
    }
}

private const val BEARER_PREFIX = "Bearer "
