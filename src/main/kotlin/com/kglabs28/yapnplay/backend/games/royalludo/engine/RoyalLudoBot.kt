package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.PieceId
import com.kglabs28.yapnplay.backend.games.royalludo.utils.DICE_FACES
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_ENTER_HOME
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_FINISH
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.RoyalLudoBoardUtils
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_CAPTURE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_DANGER
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_ESCAPE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_FINISH
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_HOME_LANE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_LEAVE_BASE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.SCORE_SAFE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.TRACK_SIZE

/**
 * Royal Ludo's computer player. On a roll phase it rolls; on a move
 * phase it scores every legal piece and plays the best:
 * finish > capture (bigger victims first) > leave base > enter the home
 * lane > land safe or pair up, then avoid squares an opponent can hit
 * with one roll, escape a piece that is under threat, and otherwise run
 * the piece that gets furthest. Ties go to the lowest piece number.
 */
class RoyalLudoBot : GameBot {

    override fun chooseAction(state: GameState, seat: SeatNumber): Action? {
        val s = state as? RoyalLudoState ?: return null
        if (s.currentSeat != seat.value) return null
        return when (s.phase) {
            Phase.AWAITING_ROLL -> RollDice
            Phase.AWAITING_MOVE -> bestPiece(s)?.let { MovePiece(PieceId(it.toString())) }
            Phase.FINISHED -> null
        }
    }

    /** The legal piece with the highest score, lowest index on ties. */
    fun bestPiece(state: RoyalLudoState): Int? =
        state.legalPieces.maxWithOrNull(compareBy<Int> { score(state, it) }.thenByDescending { it })

    private fun score(state: RoyalLudoState, piece: Int): Int {
        val roll = state.lastRoll ?: return 0
        val seat = state.currentSeat
        val colour = state.colourForSeat(seat)
        val from = state.pieceProgress(seat, piece)
        val to = if (from == PROGRESS_IN_BASE) 0 else from + roll
        var score = to

        if (to == PROGRESS_FINISH) score += SCORE_FINISH
        if (from == PROGRESS_IN_BASE) score += SCORE_LEAVE_BASE
        if (from <= PROGRESS_ENTER_HOME && to > PROGRESS_ENTER_HOME) score += SCORE_HOME_LANE

        if (to in 0..PROGRESS_ENTER_HOME) {
            val square = squareOf(colour, to)
            val safe = RoyalLudoBoardUtils.isSafeSquare(square)
            val victimProgress = loneVictimProgress(state, square, colour)
            if (!safe && victimProgress != null) score += SCORE_CAPTURE + victimProgress
            val pairsUp = ownPiecesOn(state, seat, square, except = piece) > 0
            if (safe || pairsUp) score += SCORE_SAFE
            if (!safe && !pairsUp && threatsTo(state, square, colour) > 0) score -= SCORE_DANGER
        }
        if (from in 0..PROGRESS_ENTER_HOME) {
            val square = squareOf(colour, from)
            val wasExposed = !RoyalLudoBoardUtils.isSafeSquare(square) &&
                ownPiecesOn(state, seat, square, except = piece) == 0 &&
                threatsTo(state, square, colour) > 0
            if (wasExposed) score += SCORE_ESCAPE
        }
        return score
    }

    private fun squareOf(colour: Int, progress: Int) =
        RoyalLudoBoardUtils.absoluteSquare(RoyalLudoBoardUtils.startSquareFor(colour), progress)

    /** Opponent pieces that could land on [square] with one roll. */
    private fun threatsTo(state: RoyalLudoState, square: Int, myColour: Int): Int =
        opponentPieces(state, myColour).count { (colour, progress) ->
            val distance = (square - squareOf(colour, progress) + TRACK_SIZE) % TRACK_SIZE
            distance in 1..DICE_FACES && progress + distance <= PROGRESS_ENTER_HOME
        }

    /** Progress of the single opponent piece on [square], or null if none or a protected pair. */
    private fun loneVictimProgress(state: RoyalLudoState, square: Int, myColour: Int): Int? {
        val there = opponentPieces(state, myColour).filter { (colour, progress) -> squareOf(colour, progress) == square }
        val byColour = there.groupBy { it.first }
        val lone = byColour.values.firstOrNull { it.size == 1 || !state.ludoRules.house.pairProtection }
        return lone?.first()?.second
    }

    private fun ownPiecesOn(state: RoyalLudoState, seat: Int, square: Int, except: Int): Int {
        val colour = state.colourForSeat(seat)
        return state.piecesForSeat(seat).withIndex().count { (index, progress) ->
            index != except && progress in 0..PROGRESS_ENTER_HOME && squareOf(colour, progress) == square
        }
    }

    /** (colour, progress) of every opponent piece on the shared track. */
    private fun opponentPieces(state: RoyalLudoState, myColour: Int): List<Pair<Int, Int>> =
        state.seats.map { it.value }
            .filter { state.colourForSeat(it) != myColour && it !in state.removalOrder }
            .flatMap { seat ->
                val colour = state.colourForSeat(seat)
                state.piecesForSeat(seat).filter { it in 0..PROGRESS_ENTER_HOME }.map { colour to it }
            }
}
