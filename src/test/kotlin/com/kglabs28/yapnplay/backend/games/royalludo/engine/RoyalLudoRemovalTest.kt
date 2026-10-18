package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.*
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.*
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoyalLudoRemovalTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `removing current seat passes turn`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2), SeatNumber(3)),
            _turn = RoyalLudoTurnState(SeatNumber(2), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0, 2 to 1, 3 to 2),
            pieces = mapOf(1 to listOf(-1, -1, -1, -1), 2 to listOf(-1, -1, -1, -1), 3 to listOf(-1, -1, -1, -1)),
            currentSeat = 2, phase = Phase.AWAITING_ROLL,
        )
        val result = engine.removeSeat(state, SeatNumber(2))
        val s = result as RoyalLudoState
        assertTrue(2 in s.removalOrder)
        assertTrue(s.currentSeat != 2)
    }

    @Test
    fun `removal order determines ranking`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.FINISHED.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(56, 56, 56, 56)),
            currentSeat = 1, phase = Phase.FINISHED, removalOrder = listOf(2, 3),
        )
        val outcome = engine.outcome(state)
        val places = outcome.ranking.associate { it.place to it.seat.value }
        assertEquals(1, places[1])
        assertEquals(3, places[2])
        assertEquals(2, places[3])
    }

    @Test
    fun `sole remaining seat ranks above removed`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.FINISHED.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(56, -1, -1, -1)),
            currentSeat = 1, phase = Phase.FINISHED, removalOrder = listOf(2),
        )
        val outcome = engine.outcome(state)
        val seat1 = outcome.ranking.first { it.seat.value == 1 }
        val seat2 = outcome.ranking.first { it.seat.value == 2 }
        assertTrue(seat1.place < seat2.place)
    }

    @Test
    fun `all seats removed gives cancelled outcome`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = emptyList(),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.FINISHED.name),
            seatColours = emptyMap(), pieces = emptyMap(),
            currentSeat = 1, phase = Phase.FINISHED, removalOrder = listOf(1, 2, 3, 4),
        )
        val outcome = engine.outcome(state)
        assertTrue(outcome.ranking.isNotEmpty())
        assertTrue(outcome.ranking.all { it.status == SeatStatus.REMOVED })
    }

    @Test
    fun `finishing order gives places`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = emptyList(),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.FINISHED.name),
            seatColours = emptyMap(), pieces = emptyMap(),
            currentSeat = 1, phase = Phase.FINISHED, finishingOrder = listOf(3, 1, 2),
        )
        val outcome = engine.outcome(state)
        val places = outcome.ranking.associate { it.seat.value to it.place }
        assertEquals(1, places[3])
        assertEquals(2, places[1])
        assertEquals(3, places[2])
    }
}
