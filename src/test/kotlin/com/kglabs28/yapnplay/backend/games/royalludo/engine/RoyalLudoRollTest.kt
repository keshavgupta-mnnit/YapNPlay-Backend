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
import kotlin.test.assertTrue

class RoyalLudoRollTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `piece leaves base only on 6`() {
        val state = engine.start(RoyalLudoRules.TRADITIONAL, 2, TestRandom(listOf(6)))
        val result = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(6)))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(Phase.AWAITING_MOVE, s.phase)
        assertTrue(s.legalPieces.isNotEmpty())
    }

    @Test
    fun `rolling 1 does not leave base`() {
        val state = engine.start(RoyalLudoRules.TRADITIONAL, 2, TestRandom(listOf(6)))
        val result = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(1)))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(Phase.AWAITING_ROLL, s.phase)
    }

    @Test
    fun `three consecutive sixes forfeits turn`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0, 2 to 2),
            pieces = mapOf(1 to listOf(5, 5, -1, -1), 2 to listOf(-1, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_ROLL, consecutiveSixes = 2,
        )
        val result = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(6)))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(Phase.AWAITING_ROLL, s.phase)
        assertEquals(2, s.currentSeat)
        assertEquals(0, s.consecutiveSixes)
    }

    @Test
    fun `six gives extra turn`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0, 2 to 2),
            pieces = mapOf(1 to listOf(-1, -1, -1, -1), 2 to listOf(-1, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_ROLL,
        )
        val rollResult = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(6)))
        assertIs<ApplyResult.Success>(rollResult)
        val afterRoll = rollResult.state as RoyalLudoState
        val moveResult = engine.apply(afterRoll, SeatNumber(1), MovePiece(PieceId("0")), TestRandom(listOf()))
        assertIs<ApplyResult.Success>(moveResult)
        val afterMove = moveResult.state as RoyalLudoState
        assertEquals(1, afterMove.currentSeat)
        assertEquals(Phase.AWAITING_ROLL, afterMove.phase)
    }

    @Test
    fun `piece finishes on exact roll`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(53, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_ROLL,
        )
        // Only one piece can move, so the traditional rules move it automatically.
        val rollResult = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(3)))
        assertIs<ApplyResult.Success>(rollResult)
        val afterRoll = rollResult.state as RoyalLudoState
        assertEquals(PROGRESS_FINISHED, afterRoll.piecesForSeat(1)[0])
    }

    @Test
    fun `piece cannot move on overshoot roll`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0),
            pieces = mapOf(1 to listOf(53, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_ROLL,
        )
        val result = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(4)))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(Phase.AWAITING_ROLL, s.phase)
        assertTrue(s.legalPieces.isEmpty())
    }

    @Test
    fun `no legal move passes turn`() {
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = mapOf(1 to 0, 2 to 2),
            pieces = mapOf(1 to listOf(-1, -1, -1, -1), 2 to listOf(-1, -1, -1, -1)),
            currentSeat = 1, phase = Phase.AWAITING_ROLL,
        )
        val result = engine.apply(state, SeatNumber(1), RollDice, TestRandom(listOf(2)))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(2, s.currentSeat)
    }
}

/**
 * Test dice: values are dice faces (1-6), mapped to nextInt results.
 * Empty list returns 0 (only usable where random is never read).
 */
class TestRandom(private val values: List<Int>) : GameRandom {
    private var index = 0
    override fun nextInt(bound: Int): Int {
        if (values.isEmpty()) return 0
        val v = values[index % values.size]; index++; return (v - 1) % bound
    }
}
