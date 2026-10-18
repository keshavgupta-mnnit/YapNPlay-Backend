package com.kglabs28.yapnplay.backend.domain.identity

/** Turns a secret into the value stored in the database, and checks a secret against it. */
interface SecretHasher {
    fun hash(secret: String): String

    /** Constant-time comparison, so response timing reveals nothing. */
    fun matches(secret: String, storedHash: String): Boolean
}
