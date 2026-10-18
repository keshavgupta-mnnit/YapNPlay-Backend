package com.kglabs28.yapnplay.backend.api.auth

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.security.MessageDigest

/**
 * The admin dashboard's own sign-in: `Authorization: Bearer <ADMIN_TOKEN>`.
 * Separate from user tokens; with no token configured nobody gets in.
 * The header is split by hand: Ktor's parser only accepts token68
 * characters, so a password like `abc@123!` would always be refused.
 */
class AdminTokenCheck(adminToken: String?) {
    private val expected: ByteArray? = adminToken?.takeIf { it.isNotBlank() }?.toByteArray()

    fun allows(call: ApplicationCall): Boolean {
        val expected = expected ?: return false
        val header = call.request.headers[HttpHeaders.Authorization]?.trim() ?: return false
        val scheme = header.substringBefore(' ')
        if (!scheme.equals(BEARER, ignoreCase = true) || ' ' !in header) return false
        val given = header.substringAfter(' ').trim()
        // Constant-time comparison, so response timing reveals nothing about the token.
        return MessageDigest.isEqual(expected, given.toByteArray())
    }

    private companion object {
        const val BEARER = "Bearer"
    }
}
