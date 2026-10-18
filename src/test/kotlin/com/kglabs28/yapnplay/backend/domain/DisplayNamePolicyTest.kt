package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.identity.DisplayNamePolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayNamePolicyTest {
    private val policy = DisplayNamePolicy()

    @Test
    fun `trims spaces around the name`() {
        assertEquals("Keshav", policy.normalize("  Keshav  "))
    }

    @Test
    fun `accepts 1 and 24 characters`() {
        assertEquals("K", policy.normalize("K"))
        assertEquals("x".repeat(24), policy.normalize("x".repeat(24)))
    }

    @Test
    fun `rejects empty, too long and control characters`() {
        assertNull(policy.normalize("   "))
        assertNull(policy.normalize("x".repeat(25)))
        assertNull(policy.normalize("Kes\u0007hav"))
    }
}
