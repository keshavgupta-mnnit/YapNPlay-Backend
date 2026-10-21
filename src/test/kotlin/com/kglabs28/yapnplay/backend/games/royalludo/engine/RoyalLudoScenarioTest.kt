package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_FINISH
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import kotlin.test.Test
import kotlin.test.assertEquals

/** Royal Ludo choices a good player makes. Seat 1 is red (square = progress). */
class RoyalLudoScenarioTest {
    private val bot = RoyalLudoBot()
    private val base = PROGRESS_IN_BASE
    private val home = PROGRESS_FINISH

    /** Seat 1 (red) to move with [roll]; [others] maps seat → (colour, pieces). */
    private fun state(mine: List<Int>, roll: Int, others: Map<Int, Pair<Int, List<Int>>>) = RoyalLudoState(
        _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
        _seats = (listOf(1) + others.keys).map(::SeatNumber),
        _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
        seatColours = mapOf(1 to 0) + others.mapValues { it.value.first },
        pieces = mapOf(1 to mine) + others.mapValues { it.value.second },
        currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = roll,
        legalPieces = mine.indices.filter { mine[it] in 0 until home || (mine[it] == base && roll == 6) },
    )

    @Test
    fun `it keeps clear of squares a six from base can reach`() {
        // Green (start 13) still has pieces in base: 16 can be hit by a six out and then a 3.
        val s = state(listOf(10, 30, home, home), roll = 6, others = mapOf(2 to (1 to listOf(base, base, base, home))))
        assertEquals(1, bot.bestPiece(s))
    }

    @Test
    fun `a piece safe in the home lane is moved only to finish`() {
        val s = state(listOf(52, 20, home, home), roll = 2, others = mapOf(2 to (1 to List(4) { home })))
        assertEquals(1, bot.bestPiece(s))
    }

    @Test
    fun `early on, a six brings a new piece out`() {
        val s = state(listOf(20, base, base, base), roll = 6, others = mapOf(2 to (1 to List(4) { home })))
        assertEquals(1, bot.bestPiece(s))
    }

    @Test
    fun `it would rather capture the leader's piece`() {
        // Red 2→5 hits green (leading the race, progress 44); red 20→23 hits yellow (behind, progress 49).
        val s = state(
            listOf(2, 20, base, base), roll = 3,
            others = mapOf(2 to (1 to listOf(44, home, home, home)), 3 to (2 to listOf(49, base, base, base))),
        )
        assertEquals(0, bot.bestPiece(s))
    }
}
