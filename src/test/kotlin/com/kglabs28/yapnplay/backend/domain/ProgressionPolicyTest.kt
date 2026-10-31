package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressionPolicyTest {
    private val policy = ProgressionPolicy()
    private val minute = 60_000L

    @Test
    fun `fixed xp for completing plus a fixed xp for the end position, leavers none`() {
        assertEquals(listOf(500, 400, 300, 250, 200, 150, 125, 100, 100), (1..9).map { policy.xpFor(it, left = false, durationMillis = 0) })
        assertEquals(0, policy.xpFor(1, left = true, durationMillis = 30 * minute))
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
    fun `variable xp for every minute played, up to the cap`() {
        assertEquals(550, policy.xpFor(1, left = false, durationMillis = 5 * minute + 59_000), "5 whole minutes: +50")
        assertEquals(400, policy.xpFor(4, left = false, durationMillis = 15 * minute), "4th, 15 minutes: 100 + 150 + 150")
        assertEquals(3_000, policy.xpFor(1, left = false, durationMillis = 300 * minute), "time xp stops at 250 minutes (3,000 xp)")
    }
}
