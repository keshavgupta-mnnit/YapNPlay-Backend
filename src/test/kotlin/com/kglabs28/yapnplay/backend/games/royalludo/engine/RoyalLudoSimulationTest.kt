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
import kotlin.test.assertTrue

class RoyalLudoSimulationTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `seeded games complete within turn cap`() {
        val maxTurns = 5000L
        for (seatCount in 2..4) {
            for (game in 1..100) {
                val seed = seatCount * 100000 + game
                val random = SeededRandom(seed.toLong())
                var state = engine.start(RoyalLudoRules.TRADITIONAL, seatCount, random)
                var turns = 0L
                while (!engine.isFinished(state) && turns < maxTurns) {
                    val s = state as RoyalLudoState
                    val result = engine.autoplay(state, SeatNumber(s.currentSeat), random)
                    assertIs<ApplyResult.Success>(result)
                    state = result.state
                    turns++
                    checkInvariants(state as RoyalLudoState)
                }
                assertTrue(turns < maxTurns, "Game seed $seed exceeded turn cap")
            }
        }
    }

    private fun checkInvariants(state: RoyalLudoState) {
        assertTrue(state.currentSeat in state.seats.map { it.value })
        for ((_, pieces) in state.pieces) {
            for (p in pieces) {
                assertTrue(p == PROGRESS_IN_BASE || p in 0..PROGRESS_FINISHED, "Invalid progress: $p")
            }
        }
        for ((_, pieces) in state.pieces) assertEquals(PIECES_PER_COLOUR, pieces.size)
    }

    @Test
    fun `random illegal actions are rejected`() {
        val random = SeededRandom(42)
        val state = engine.start(RoyalLudoRules.TRADITIONAL, 2, random)
        val badMove = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = state.seats, _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = (state as RoyalLudoState).seatColours,
            pieces = state.pieces,
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = 3, legalPieces = listOf(0),
        )
        val result = engine.apply(badMove, SeatNumber(1), MovePiece(PieceId("1")), random)
        assertIs<ApplyResult.Rejected>(result)
    }
}

class SeededRandom(private val seed: Long) : GameRandom {
    private var state = seed
    override fun nextInt(bound: Int): Int {
        state = (state * 6364136223846793005 + 1442695040888963407) and 0x7FFFFFFFFFFFFFFF
        return ((state ushr 33) % bound).toInt()
    }
}
