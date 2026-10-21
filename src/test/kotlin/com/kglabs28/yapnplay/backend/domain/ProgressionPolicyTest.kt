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

    @Test
    fun `xp grows with how long the match ran, in hundreds`() {
        val minute = 60_000L
        assertEquals(400, policy.xpFor(1, left = false, durationMillis = 10 * minute), "10 minutes: the full amount")
        assertEquals(200, policy.xpFor(1, left = false, durationMillis = 5 * minute), "5 minutes: half")
        assertEquals(1_200, policy.xpFor(1, left = false, durationMillis = 30 * minute), "30 minutes: three times")
        assertEquals(3_600, policy.xpFor(1, left = false, durationMillis = 90 * minute), "keeps growing while they keep playing")
        assertEquals(4_800, policy.xpFor(1, left = false, durationMillis = 300 * minute), "capped at 12x (2 hours)")
        assertEquals(100, policy.xpFor(4, left = false, durationMillis = 1 * minute), "never below 100 for finishing")
        assertEquals(0, policy.xpFor(1, left = true, durationMillis = 30 * minute))
    }
}
