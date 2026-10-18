package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.*
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.*
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.*
import com.kglabs28.yapnplay.backend.games.royalludo.utils.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoyalLudoCaptureTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `lone opposing piece is captured`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(4, -1, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(10, s.piecesForSeat(1)[0])
        assertEquals(PROGRESS_IN_BASE, s.piecesForSeat(2)[0])
    }

    @Test
    fun `capture on safe square does not happen`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(2, -1, -1, -1), 2 to listOf(21, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(21, s.piecesForSeat(2)[0])
    }

    @Test
    fun `capture gives extra turn`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(4, -1, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(1, s.currentSeat)
        assertEquals(Phase.AWAITING_ROLL, s.phase)
    }

    @Test
    fun `capturesSendToBase false disables capture`() {
        val rules = RoyalLudoRules.TRADITIONAL.copy(
            mandatory = RoyalLudoRules.TRADITIONAL.mandatory.copy(capturesSendToBase = false)
        )
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = rules,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(4, -1, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(10, s.piecesForSeat(1)[0])
        assertEquals(23, s.piecesForSeat(2)[0])
    }

    @Test
    fun `capture in home column does not happen`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(54, -1, -1, -1), 2 to listOf(54, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 1, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(54, s.piecesForSeat(2)[0])
    }
}
