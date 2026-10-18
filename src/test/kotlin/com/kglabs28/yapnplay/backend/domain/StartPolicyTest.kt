package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.domain.rooms.LiveMember
import com.kglabs28.yapnplay.backend.domain.rooms.StartPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartPolicyTest {
    private val policy = StartPolicy()
    private val ludo = RoyalLudoEngine() // 4 to 4 players

    private fun ready(id: String, readyAt: Long) = LiveMember(id, id, 0, online = true, onlineSince = 0, ready = true, readyAt = readyAt)
    private fun idle(id: String) = LiveMember(id, id, 0, online = true, onlineSince = 0)

    @Test
    fun `the leader counts as ready`() {
        val members = listOf(idle("lead"), ready("a", 1))
        assertEquals(listOf("lead", "a"), policy.readyHumans(members, "lead").map { it.userId })
    }

    @Test
    fun `two ready humans need bot fill when the game needs four`() {
        val members = listOf(idle("lead"), ready("a", 1))
        assertFalse(policy.canStart(members, "lead", ludo, botFill = false))
        assertTrue(policy.canStart(members, "lead", ludo, botFill = true))
    }

    @Test
    fun `one human can never start, even with bots`() {
        assertFalse(policy.canStart(listOf(idle("lead")), "lead", ludo, botFill = true))
    }

    @Test
    fun `bots fill only up to the minimum`() {
        val plan = policy.plan(listOf(idle("lead"), ready("a", 1)), "lead", ludo, botFill = true, houseRules = emptyMap())
        assertEquals(listOf("lead", "a"), plan.players)
        assertEquals(2, plan.botCount)
    }

    @Test
    fun `with six ready the first four by ready time play and the rest watch`() {
        val members = listOf(idle("lead"), ready("e", 5), ready("a", 1), ready("c", 3), ready("b", 2), ready("d", 4), idle("x"))
        val plan = policy.plan(members, "lead", ludo, botFill = false, houseRules = emptyMap())
        assertEquals(listOf("lead", "a", "b", "c"), plan.players)
        assertEquals(0, plan.botCount)
        assertEquals(setOf("d", "e", "x"), plan.spectators.toSet())
    }
}
