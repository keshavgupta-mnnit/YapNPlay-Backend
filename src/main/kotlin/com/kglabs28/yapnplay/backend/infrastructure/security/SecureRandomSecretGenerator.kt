package com.kglabs28.yapnplay.backend.infrastructure.security

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import java.security.SecureRandom
import java.util.Base64

/** 32 random bytes as URL-safe Base64: 43 characters, never a dot. */
class SecureRandomSecretGenerator(private val random: SecureRandom = SecureRandom()) : SecretGenerator {

    override fun newSecret(): String {
        val bytes = ByteArray(BackendConstants.SECRET_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
