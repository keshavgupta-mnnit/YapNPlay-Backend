package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/** Checks a bearer token `<userId>.<secret>`; returns the user or null. */
class AuthenticateUseCase(
    private val users: UserRepository,
    private val hasher: SecretHasher,
) {
    suspend operator fun invoke(token: String): User? {
        val userId = token.substringBefore(BackendConstants.BEARER_SEPARATOR, missingDelimiterValue = "")
        val secret = token.substringAfter(BackendConstants.BEARER_SEPARATOR, missingDelimiterValue = "")
        if (userId.isEmpty() || secret.isEmpty()) return null
        val user = users.findById(userId) ?: return null
        return user.takeIf { hasher.matches(secret, it.secretHash) }
    }
}
