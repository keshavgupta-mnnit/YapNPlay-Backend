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

class RoyalLudoPairTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `pair is protected from capture`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(2), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(10, 10, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 2, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(2), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(10, s.piecesForSeat(1)[0])
        assertEquals(10, s.piecesForSeat(1)[1])
    }

    @Test
    fun `three pieces dropping to two still protected`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(10, 10, 10, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0, 1, 2),
        )
        val result = engine.apply(state, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(2, s.piecesForSeat(1).count { it == 10 })
    }

    @Test
    fun `pair protection off allows normal capture`() {
        val rules = RoyalLudoRules.TRADITIONAL.copy(
            house = RoyalLudoRules.TRADITIONAL.house.copy(pairProtection = false)
        )
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = rules,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(2), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 3),
            pieces = mapOf(1 to listOf(10, 10, -1, -1), 2 to listOf(23, -1, -1, -1)),
            currentSeat = 2, phase = Phase.AWAITING_MOVE, lastRoll = 6, legalPieces = listOf(0),
        )
        val result = engine.apply(state, SeatNumber(2), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(PROGRESS_IN_BASE, s.piecesForSeat(1).first { it != 10 })
    }
}
