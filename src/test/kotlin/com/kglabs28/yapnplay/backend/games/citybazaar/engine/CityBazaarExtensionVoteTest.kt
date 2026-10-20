package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.EndMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ExtendMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ReachTimeLimit
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** City Bazaar waits at its round or time limit (the match layer runs the vote), then extends or ends; the bank keeps totals. */
class CityBazaarExtensionVoteTest {
    private val engine = CityBazaarEngine()

    private class Dice(private val faces: List<Int>) : GameRandom {
        private var i = 0
        override fun nextInt(bound: Int): Int = (faces[i++ % faces.size] - 1) % bound
    }

    private var lastEvents: List<GameEvent> = emptyList()

    private fun play(state: CityBazaarState, seat: Int, action: Action, faces: List<Int> = listOf(1)): CityBazaarState {
        val result = engine.apply(state, SeatNumber(seat), action, Dice(faces))
        assertIs<ApplyResult.Success>(result, "seat $seat: $action")
        lastEvents = result.events
        return result.state as CityBazaarState
    }

    private val rules = CityBazaarRules.STANDARD.copy(
        house = CityBazaarHouseRules(roundLimit = 1, timeLimitMinutes = 30, extensionRounds = 30, extensionMinutes = 15),
    )

    /** Seat 3 ends round 1 of a 1-round match by landing on START. */
    private fun atRoundLimit(): CityBazaarState {
        val start = engine.start(rules, 3, Dice(listOf(1))) as CityBazaarState
        return play(start.copy(currentSeat = 3, positions = mapOf(1 to 0, 2 to 0, 3 to 34)), 3, RollDice, listOf(1, 1))
    }

    @Test
    fun `reaching the round limit with players in waits for the vote`() {
        val s = atRoundLimit()
        assertEquals(CityBazaarPhase.EXTENSION_VOTE, s.phase)
        assertEquals(CityBazaarLimit.ROUNDS, s.limitHit)
        assertEquals(1, s.currentSeat, "the next player is ready once play resumes")
    }

    @Test
    fun `going on adds rounds and the next turn starts`() {
        val s = play(atRoundLimit(), 1, ExtendMatch(extraRounds = 30, extraMinutes = 15))
        assertEquals(CityBazaarPhase.ROLL, s.phase)
        assertEquals(31, s.cbRules.house.roundLimit)
        assertNull(s.limitHit)
        assertTrue(lastEvents.any { it is TurnPassed && it.seat == 1 })
    }

    @Test
    fun `stopping ends the match`() {
        assertEquals(CityBazaarPhase.FINISHED, play(atRoundLimit(), 1, EndMatch).phase)
    }

    @Test
    fun `the time limit pauses between turns and going on adds minutes`() {
        val start = engine.start(rules, 3, Dice(listOf(1))) as CityBazaarState
        val paused = play(start, 1, ReachTimeLimit)
        assertEquals(CityBazaarPhase.EXTENSION_VOTE, paused.phase)
        assertEquals(CityBazaarLimit.TIME, paused.limitHit)
        assertEquals(45, play(paused, 1, ExtendMatch(30, 15)).cbRules.house.timeLimitMinutes)
    }

    @Test
    fun `the bank counts what it pays and receives`() {
        val s = atRoundLimit()
        assertEquals(1_500L, s.bankPaid, "the START bonus comes from the bank")
        assertEquals(0L, s.bankReceived)
    }
}
