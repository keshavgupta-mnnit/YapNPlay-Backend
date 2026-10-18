package com.kglabs28.yapnplay.backend.infrastructure.security

import com.kglabs28.yapnplay.backend.domain.identity.SecretHasher
import java.security.MessageDigest

/**
 * SHA-256 of the secret, as hex. A fast hash is fine here because the
 * secrets are long random values, not passwords people choose.
 */
class Sha256SecretHasher : SecretHasher {

    override fun hash(secret: String): String =
        MessageDigest.getInstance(ALGORITHM).digest(secret.toByteArray(Charsets.UTF_8)).toHex()

    override fun matches(secret: String, storedHash: String): Boolean =
        MessageDigest.isEqual(hash(secret).toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))

    private fun ByteArray.toHex(): String = joinToString("") { HEX_FORMAT.format(it) }

    private companion object {
        const val ALGORITHM = "SHA-256"
        const val HEX_FORMAT = "%02x"
    }
}
