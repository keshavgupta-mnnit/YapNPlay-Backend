package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionPolicyTest {
    private val policy = ProgressionPolicy()

    @Test
    fun `everyone who finishes gets xp in hundreds, winners a little extra, leavers none`() {
        assertEquals(listOf(400, 300, 200, 100, 100), (1..5).map { policy.xpFor(it, left = false) })
        assertEquals(0, policy.xpFor(1, left = true))
    }

    @Test
    fun `a new level every 1000 xp`() {
        assertEquals(1, policy.levelFor(0))
        assertEquals(1, policy.levelFor(999))
        assertEquals(2, policy.levelFor(1_000))
        assertEquals(11, policy.levelFor(10_400))
        assertEquals(1_000, policy.levelStartXp(2))
    }
}
