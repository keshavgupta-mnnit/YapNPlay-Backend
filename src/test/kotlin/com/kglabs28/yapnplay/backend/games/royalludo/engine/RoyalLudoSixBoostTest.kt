package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Waiting in base for a six: the dice stay random, but every miss makes the next six a little likelier (capped). */
class RoyalLudoSixBoostTest {
    private val engine = RoyalLudoEngine()

    private fun start(misses: Int, pieces: List<Int> = List(4) { PROGRESS_IN_BASE }, boost: Int = 8, cap: Int = 50): RoyalLudoState {
        val s = engine.start(RoyalLudoRules.TRADITIONAL, 2, Seeded(0)) as RoyalLudoState
        return s.copy(sixBoostPerMissPct = boost, sixChanceCapPct = cap, baseMisses = mapOf(1 to misses), pieces = s.pieces + (1 to pieces))
    }

    private class Seeded(seed: Int) : GameRandom {
        private val random = Random(seed)
        override fun nextInt(bound: Int) = random.nextInt(bound)
    }

    private fun result(s: RoyalLudoState, random: GameRandom) = engine.apply(s, SeatNumber(1), RollDice, random) as ApplyResult.Success

    /** Share of sixes over many seeded rolls from [s]. */
    private fun sixRate(s: RoyalLudoState): Double {
        val rolls = 20_000
        val sixes = (0 until rolls).count { seed -> result(s, Seeded(seed)).events.filterIsInstance<DiceRolled>().first().value == 6 }
        return sixes.toDouble() / rolls
    }

    @Test
    fun `the first roll in base is fair`() {
        assertEquals(1.0 / 6, sixRate(start(misses = 0)), 0.02)
    }

    @Test
    fun `after 3 misses a six is likelier but not certain`() {
        // 1/6 + 3 × 8 % ≈ 40.7 %.
        assertEquals(0.407, sixRate(start(misses = 3)), 0.02)
    }

    @Test
    fun `the chance never goes above the cap`() {
        assertEquals(0.5, sixRate(start(misses = 20)), 0.02)
    }

    @Test
    fun `a player with a piece out and the setting at 0 roll fair dice`() {
        assertEquals(1.0 / 6, sixRate(start(misses = 5, pieces = listOf(10, PROGRESS_IN_BASE, PROGRESS_IN_BASE, PROGRESS_IN_BASE))), 0.02)
        assertEquals(1.0 / 6, sixRate(start(misses = 9, boost = 0)), 0.02)
    }

    @Test
    fun `misses are counted while stuck and reset by a six`() {
        val missed = (0 until 50).map { result(start(misses = 2), Seeded(it)) }
        missed.forEach { r ->
            val six = r.events.filterIsInstance<DiceRolled>().first().value == 6
            assertEquals(if (six) 0 else 3, (r.state as RoyalLudoState).baseMisses[1])
        }
        assertTrue(missed.any { it.events.filterIsInstance<DiceRolled>().first().value != 6 })
    }
}
