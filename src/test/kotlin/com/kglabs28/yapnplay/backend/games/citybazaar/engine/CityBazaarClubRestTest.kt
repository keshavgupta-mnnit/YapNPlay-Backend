package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CLUB_TILE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.REST_HOUSE_TILE
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Club: the lander pays everyone; Rest House: everyone pays the lander. One Money event per pair, no skipped turn. */
class CityBazaarClubRestTest {
    private val engine = CityBazaarEngine()

    private class Dice(private val faces: List<Int>) : GameRandom {
        private var i = 0
        override fun nextInt(bound: Int): Int = (faces[i++ % faces.size] - 1) % bound
    }

    private fun start(each: Int = 50): CityBazaarState {
        val rules = CityBazaarRules.STANDARD.copy(house = CityBazaarHouseRules(clubRestEach = each))
        return engine.start(rules, 4, Dice(listOf(1))) as CityBazaarState
    }

    /** Seat 1 rolls 1 + 2 from [from]. */
    private fun roll(state: CityBazaarState, from: Int): ApplyResult.Success {
        val result = engine.apply(state.copy(positions = mapOf(1 to from, 2 to 0, 3 to 0, 4 to 0)), SeatNumber(1), RollDice, Dice(listOf(1, 2)))
        return assertIs<ApplyResult.Success>(result)
    }

    @Test
    fun `club - the lander pays every other player`() {
        val s0 = start()
        val result = roll(s0, CLUB_TILE - 3)
        val s = result.state as CityBazaarState
        assertEquals(s0.cashOf(1) - 150, s.cashOf(1))
        listOf(2, 3, 4).forEach { assertEquals(s0.cashOf(it) + 50, s.cashOf(it)) }
        val money = result.events.filterIsInstance<MoneyMoved>().filter { it.reason == MoneyReason.CLUB }
        assertEquals(listOf(2, 3, 4), money.map { it.to })
        money.forEach { assertEquals(1, it.from); assertEquals(50, it.amount); assertEquals(CLUB_TILE, it.tile) }
    }

    @Test
    fun `rest house - every other player pays the lander, and nobody skips a turn`() {
        val s0 = start(each = 75)
        val result = roll(s0, REST_HOUSE_TILE - 3)
        val s = result.state as CityBazaarState
        assertEquals(s0.cashOf(1) + 225, s.cashOf(1))
        listOf(2, 3, 4).forEach { assertEquals(s0.cashOf(it) - 75, s.cashOf(it)) }
        val money = result.events.filterIsInstance<MoneyMoved>().filter { it.reason == MoneyReason.REST }
        assertEquals(listOf(2, 3, 4), money.map { it.from })
        money.forEach { assertEquals(1, it.to); assertEquals(REST_HOUSE_TILE, it.tile) }
        assertEquals(emptySet(), s.skipNext)
    }

    @Test
    fun `modern city names`() {
        assertEquals(listOf("Shimla", "Kolkata", "Ooty", "Kochi"), listOf(14, 25, 32, 33).map { CityBazaarBoard.tile(it).name })
    }
}
