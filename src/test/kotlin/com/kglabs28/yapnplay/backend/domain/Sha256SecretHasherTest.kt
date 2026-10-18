package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.infrastructure.security.SecureRandomSecretGenerator
import com.kglabs28.yapnplay.backend.infrastructure.security.Sha256SecretHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Sha256SecretHasherTest {
    private val hasher = Sha256SecretHasher()
    private val secrets = SecureRandomSecretGenerator()

    @Test
    fun `hash is 64 hex characters and never the secret itself`() {
        val secret = secrets.newSecret()
        val hash = hasher.hash(secret)
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
        assertNotEquals(secret, hash)
    }

    @Test
    fun `only the right secret matches`() {
        val secret = secrets.newSecret()
        val hash = hasher.hash(secret)
        assertTrue(hasher.matches(secret, hash))
        assertFalse(hasher.matches(secret + "x", hash))
    }

    @Test
    fun `secrets are long, random and contain no dot`() {
        val a = secrets.newSecret()
        val b = secrets.newSecret()
        assertNotEquals(a, b)
        assertTrue(a.length >= 43 && '.' !in a)
    }
}
