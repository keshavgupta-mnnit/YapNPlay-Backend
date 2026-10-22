package com.kglabs28.yapnplay.backend.api.auth

import com.kglabs28.yapnplay.backend.application.admin.AdminAuthUseCase
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccount
import com.kglabs28.yapnplay.backend.domain.admin.AdminPermissions
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall

/**
 * Who is calling an admin route: the admin behind a dashboard session
 * token, or (break-glass, for the server owner) the server's ADMIN_TOKEN,
 * which acts as an account named [TOKEN_ADMIN] with every permission.
 */
class AdminAuth(
    private val tokenCheck: AdminTokenCheck,
    private val auth: AdminAuthUseCase,
) {
    suspend fun account(call: ApplicationCall): AdminAccount? {
        if (tokenCheck.allows(call)) return TOKEN_ACCOUNT
        val token = bearer(call) ?: return null
        return auth.accountFor(token)
    }

    companion object {
        const val TOKEN_ADMIN = "ADMIN_TOKEN"
        private val TOKEN_ACCOUNT = AdminAccount(TOKEN_ADMIN, "", "", AdminPermissions.ALL, 0, TOKEN_ADMIN)
        private const val BEARER = "Bearer"

        /** The bearer token, split by hand as in [AdminTokenCheck]. */
        fun bearer(call: ApplicationCall): String? {
            val header = call.request.headers[HttpHeaders.Authorization]?.trim() ?: return null
            if (!header.substringBefore(' ').equals(BEARER, ignoreCase = true) || ' ' !in header) return null
            return header.substringAfter(' ').trim().takeIf { it.isNotEmpty() }
        }
    }
}
