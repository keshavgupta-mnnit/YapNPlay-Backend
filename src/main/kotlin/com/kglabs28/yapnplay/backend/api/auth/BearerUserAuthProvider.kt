package com.kglabs28.yapnplay.backend.api.auth

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.application.identity.AuthenticateUseCase
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.request.path
import io.ktor.server.response.respond

/**
 * `Authorization: Bearer <userId>.<secret>` on every call. In DEV_MODE the
 * WebSocket may pass `?token=<userId>.<secret>` instead, because browser
 * consoles cannot set headers on a WebSocket.
 */
class BearerUserAuthProvider(config: Config) : AuthenticationProvider(config) {

    private val authenticate = config.authenticate
    private val devMode = config.devMode

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val token = headerToken(call.request.parseAuthorizationHeader())
            ?: call.request.queryParameters[BackendConstants.WS_DEV_TOKEN_QUERY]
                ?.takeIf { devMode && call.request.path() == BackendConstants.WS_PATH }
        val user = token?.let { authenticate(it) }
        if (user != null) {
            context.principal(UserPrincipal(user.id))
            return
        }
        val cause = if (token == null) AuthenticationFailedCause.NoCredentials else AuthenticationFailedCause.InvalidCredentials
        context.challenge(CHALLENGE_KEY, cause) { challenge, challengedCall ->
            challengedCall.respond(
                HttpStatusCode.Unauthorized,
                ErrorBody(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED),
            )
            challenge.complete()
        }
    }

    private fun headerToken(header: HttpAuthHeader?): String? =
        (header as? HttpAuthHeader.Single)?.takeIf { it.authScheme.equals(SCHEME, ignoreCase = true) }?.blob

    class Config(
        name: String,
        val authenticate: AuthenticateUseCase,
        val devMode: Boolean,
    ) : AuthenticationProvider.Config(name)

    companion object {
        const val NAME = "bearer-user"
        private const val SCHEME = "Bearer"
        private const val CHALLENGE_KEY = "BearerUser"
    }
}
