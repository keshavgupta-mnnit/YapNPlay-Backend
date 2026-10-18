package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.royalludo.utils.*

import com.kglabs28.yapnplay.backend.games.base.identity.*
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.*
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoyalLudoAutoplayTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `autoplay finishes when possible`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(54, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 2, legalPieces = listOf(0),
        )
        val result = engine.autoplay(state, SeatNumber(1), SeededRandom(1L))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(PROGRESS_FINISHED, s.piecesForSeat(1)[0])
    }

    @Test
    fun `autoplay captures when possible`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(4, -1, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.autoplay(state, SeatNumber(1), SeededRandom(1L))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(PROGRESS_IN_BASE, s.piecesForSeat(2)[0])
    }

    @Test
    fun `autoplay leaves base when possible`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(-1, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0, 1, 2, 3),
        )
        val result = engine.autoplay(state, SeatNumber(1), SeededRandom(1L))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(3, s.piecesForSeat(1).count { it == PROGRESS_IN_BASE })
        assertEquals(1, s.piecesForSeat(1).count { it == 0 })
    }

    @Test
    fun `autoplay is deterministic`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(4, 3, -1, -1), 2 to listOf(-1, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0, 1),
        )
        val r1 = engine.autoplay(state, SeatNumber(1), SeededRandom(1L)) as ApplyResult.Success
        val r2 = engine.autoplay(state, SeatNumber(1), SeededRandom(1L)) as ApplyResult.Success
        val s1 = r1.state as RoyalLudoState
        val s2 = r2.state as RoyalLudoState
        assertEquals(s1.pieces, s2.pieces)
        assertEquals(s1.currentSeat, s2.currentSeat)
    }
}
