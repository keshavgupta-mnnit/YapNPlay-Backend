package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.PieceId
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_FINISHED
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** When the second-to-last player brings their last piece home, the match ends and the one left is last. */
class RoyalLudoLastPlayerTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `the match ends when only one player is still racing`() {
        val home = List(4) { PROGRESS_FINISHED }
        val state = RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = RoyalLudoRules.TRADITIONAL,
            _seats = listOf(SeatNumber(1), SeatNumber(2), SeatNumber(3)),
            _turn = RoyalLudoTurnState(SeatNumber(2), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to 1, 3 to 2),
            pieces = mapOf(1 to home, 2 to listOf(PROGRESS_FINISHED - 1) + home.drop(1), 3 to List(4) { PROGRESS_IN_BASE }),
            currentSeat = 2, phase = Phase.AWAITING_MOVE, lastRoll = 1, legalPieces = listOf(0),
            finishingOrder = listOf(1),
        )
        val result = engine.apply(state, SeatNumber(2), MovePiece(PieceId("0")), TestRandom(emptyList()))
        assertIs<ApplyResult.Success>(result)
        val s = result.state as RoyalLudoState
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(listOf(1, 2), s.finishingOrder)
        assertEquals(listOf(1, 2, 3), engine.outcome(s).ranking.sortedBy { it.place }.map { it.seat.value })
    }
}
