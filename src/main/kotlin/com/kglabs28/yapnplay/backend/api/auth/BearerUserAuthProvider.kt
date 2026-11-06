package com.kglabs28.yapnplay.backend.api.auth

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.application.identity.AuthenticateUseCase
import com.kglabs28.yapnplay.backend.application.identity.WsTicketIssuer
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
 * `Authorization: Bearer <userId>.<secret>` on every call. Browsers cannot
 * set headers on a WebSocket, so `/ws` also takes a one-time
 * `?ticket=` (the web app, 0.12.5), and in DEV_MODE `?token=<userId>.<secret>`
 * (browser consoles).
 */
class BearerUserAuthProvider(config: Config) : AuthenticationProvider(config) {

    private val authenticate = config.authenticate
    private val devMode = config.devMode
    private val tickets = config.tickets

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        if (call.request.path() == BackendConstants.WS_PATH) {
            call.request.queryParameters[BackendConstants.WS_TICKET_QUERY]?.let(tickets::redeem)?.let {
                context.principal(UserPrincipal(it))
                return
            }
        }
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
        val tickets: WsTicketIssuer,
    ) : AuthenticationProvider.Config(name)

    companion object {
        const val NAME = "bearer-user"
        private const val SCHEME = "Bearer"
        private const val CHALLENGE_KEY = "BearerUser"
    }
}
