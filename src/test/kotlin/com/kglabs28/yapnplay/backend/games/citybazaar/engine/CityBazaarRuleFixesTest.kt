package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_FINE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_TILE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_TRIES
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Jail is decided on the spot, a jailed player starts the next turn free, and rent is totalled. */
class CityBazaarRuleFixesTest {
    private val engine = CityBazaarEngine()

    /** Dice come out as the given faces, in order. */
    private class Dice(private val faces: List<Int>) : GameRandom {
        private var i = 0
        override fun nextInt(bound: Int): Int = (faces[i++ % faces.size] - 1) % bound
    }

    private fun start(): CityBazaarState = engine.start(CityBazaarRules.STANDARD, 3, Dice(listOf(1))) as CityBazaarState

    private fun play(state: CityBazaarState, seat: Int, action: Action, faces: List<Int> = listOf(1)): CityBazaarState {
        val result = engine.apply(state, SeatNumber(seat), action, Dice(faces))
        assertIs<ApplyResult.Success>(result, "seat $seat: $action")
        return result.state as CityBazaarState
    }

    @Test
    fun `landing in jail asks for the decision on that same turn`() {
        val s = play(start().copy(positions = mapOf(1 to 5, 2 to 0, 3 to 0)), 1, RollDice, listOf(1, 3))
        assertEquals(JAIL_TILE, s.positions[1])
        assertEquals(1, s.currentSeat, "still the jailed player's turn")
        assertEquals(CityBazaarPhase.JAIL_ROLL, s.phase)
        assertEquals(JAIL_TRIES, s.jailTriesLeft)
        assertTrue(s.inJail(1))
    }

    @Test
    fun `the fine can be paid straight away, before any roll`() {
        val jailed = play(start().copy(positions = mapOf(1 to 5, 2 to 0, 3 to 0)), 1, RollDice, listOf(1, 3))
        val cash = jailed.cashOf(1)
        val s = play(jailed, 1, PayJailFine)
        assertEquals(cash - JAIL_FINE, s.cashOf(1))
        assertFalse(s.inJail(1))
        assertEquals(2, s.currentSeat)
    }

    @Test
    fun `staying takes half rent and the next turn starts free`() {
        val jailed = play(start().copy(positions = mapOf(1 to 5, 2 to 0, 3 to 0)), 1, RollDice, listOf(1, 3))
        val stayed = play(jailed, 1, StayInJail)
        assertTrue(1 in stayed.halfRent)
        assertEquals(2, stayed.currentSeat)
        // Seat 3 lands on START (no decision), so the turn comes back to seat 1.
        val back = play(stayed.copy(currentSeat = 3, positions = stayed.positions + (3 to 34)), 3, RollDice, listOf(1, 1))
        assertEquals(1, back.currentSeat)
        assertEquals(CityBazaarPhase.ROLL, back.phase, "a normal roll, no jail rolls again")
        assertFalse(back.inJail(1))
        assertFalse(1 in back.halfRent)
    }

    @Test
    fun `rent is totalled per owner and per tile`() {
        val s0 = start().copy(owners = mapOf(1 to 2), positions = mapOf(1 to 35, 2 to 0, 3 to 0))
        val s = play(s0, 1, RollDice, listOf(1, 1))
        assertEquals(1, s.positions[1])
        val rent = s0.cashOf(1) + START_BONUS_FOR_TEST - s.cashOf(1)
        assertTrue(rent > 0)
        assertEquals(rent, s.rentBySeat[2])
        assertEquals(rent, s.rentByTile[1])
    }

    private companion object {
        const val START_BONUS_FOR_TEST = 1_500
    }
}
