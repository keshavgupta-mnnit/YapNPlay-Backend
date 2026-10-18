package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import kotlinx.serialization.Serializable

@Serializable
data class RoyalLudoTurnState(
    override val seat: SeatNumber,
    override val phase: String,
) : TurnState

@Serializable
enum class Phase { AWAITING_ROLL, AWAITING_MOVE, FINISHED }

@Serializable
class RoyalLudoState(
    private val _gameId: GameId,
    private val _rules: RoyalLudoRules,
    private val _seats: List<SeatNumber>,
    private val _turn: RoyalLudoTurnState,
    val seatColours: Map<Int, Int>,
    val pieces: Map<Int, List<Int>>,
    val currentSeat: Int,
    val phase: Phase,
    val lastRoll: Int? = null,
    val legalPieces: List<Int> = emptyList(),
    val consecutiveSixes: Int = 0,
    val finishingOrder: List<Int> = emptyList(),
    val removalOrder: List<Int> = emptyList(),
    val turnCounter: Long = 0,
    /** Board as it was when the current seat's turn began (a third six puts it back). */
    val turnStartPieces: Map<Int, List<Int>> = pieces,
) : GameState {
    override val gameId: GameId get() = _gameId
    override val rules: GameRules get() = _rules
    override val seats: List<SeatNumber> get() = _seats
    override val turn: TurnState get() = _turn
    val ludoRules: RoyalLudoRules get() = _rules

    fun colourForSeat(seat: Int): Int = seatColours[seat] ?: error("No colour for seat $seat")
    fun piecesForSeat(seat: Int): List<Int> = pieces[seat] ?: error("No pieces for seat $seat")
    fun pieceProgress(seat: Int, piece: Int): Int = piecesForSeat(seat)[piece]

    fun copy(
        rules: RoyalLudoRules = this._rules,
        seats: List<SeatNumber> = this._seats,
        turn: RoyalLudoTurnState = this._turn,
        seatColours: Map<Int, Int> = this.seatColours,
        pieces: Map<Int, List<Int>> = this.pieces,
        currentSeat: Int = this.currentSeat,
        phase: Phase = this.phase,
        lastRoll: Int? = this.lastRoll,
        legalPieces: List<Int> = this.legalPieces,
        consecutiveSixes: Int = this.consecutiveSixes,
        finishingOrder: List<Int> = this.finishingOrder,
        removalOrder: List<Int> = this.removalOrder,
        turnCounter: Long = this.turnCounter,
        turnStartPieces: Map<Int, List<Int>> = this.turnStartPieces,
    ): RoyalLudoState = RoyalLudoState(
        _gameId, _rules, _seats, _turn, seatColours, pieces,
        currentSeat, phase, lastRoll, legalPieces, consecutiveSixes,
        finishingOrder, removalOrder, turnCounter, turnStartPieces,
    )
}
