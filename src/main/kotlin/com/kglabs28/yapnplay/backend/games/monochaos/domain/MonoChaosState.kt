package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import kotlinx.serialization.Serializable

/**
 * A MONO: Complete Chaos match.
 *
 * [activeStackPenalty] is the whole accumulated penalty waiting to be
 * taken; [stackTopValue] is the value of the card that put it there, and
 * it is that value a stacker must equal or beat.
 */
@Serializable
data class MonoChaosState(
    private val _gameId: GameId,
    private val _rules: MonoChaosRules,
    private val _seats: List<SeatNumber>,
    private val _turn: MonoChaosTurnState,
    val hands: Map<Int, List<MonoChaosCard>>,
    val drawPile: List<MonoChaosCard>,
    val discardPile: List<MonoChaosCard>,
    val currentSeat: Int,
    val phase: MonoChaosPhase,
    val topCardColor: MonoChaosConstants.Color?,
    val turnCounter: Long = 0,
    val monoCallWindowExpiry: Long = 0,
    val monoWindowSeat: Int? = null,
    val monoCalledSeat: Int? = null,
    val lastActionSeat: Int? = null,
    val lastActionCard: MonoChaosCard? = null,
    val activeStackPenalty: Int = 0,
    /** The penalty value of the last draw card played; stacks must match it. */
    val stackTopValue: Int = 0,
    val reversed: Boolean = false,
    val drawnCardIndex: Int? = null,
    val pendingSwapSeat: Int? = null,
    val eliminatedSeats: Set<Int> = emptySet(),
    /** Seats that left the table; kept so the final ranking can place them. */
    val removedSeats: Set<Int> = emptySet(),
) : GameState {
    override val gameId: GameId get() = _gameId
    override val rules: GameRules get() = _rules
    override val seats: List<SeatNumber> get() = _seats
    override val turn: TurnState get() = _turn
    val monoChaosRules: MonoChaosRules get() = _rules

    fun handForSeat(seat: Int): List<MonoChaosCard> = hands[seat] ?: emptyList()
    fun cardCountForSeat(seat: Int): Int = hands[seat]?.size ?: 0
    fun isEliminated(seat: Int): Boolean = eliminatedSeats.contains(seat)

    fun copy(
        rules: MonoChaosRules = this._rules,
        seats: List<SeatNumber> = this._seats,
        turn: MonoChaosTurnState = this._turn,
        hands: Map<Int, List<MonoChaosCard>> = this.hands,
        drawPile: List<MonoChaosCard> = this.drawPile,
        discardPile: List<MonoChaosCard> = this.discardPile,
        currentSeat: Int = this.currentSeat,
        phase: MonoChaosPhase = this.phase,
        topCardColor: MonoChaosConstants.Color? = this.topCardColor,
        turnCounter: Long = this.turnCounter,
        monoCallWindowExpiry: Long = this.monoCallWindowExpiry,
        monoWindowSeat: Int? = this.monoWindowSeat,
        monoCalledSeat: Int? = this.monoCalledSeat,
        lastActionSeat: Int? = this.lastActionSeat,
        lastActionCard: MonoChaosCard? = this.lastActionCard,
        activeStackPenalty: Int = this.activeStackPenalty,
        stackTopValue: Int = this.stackTopValue,
        reversed: Boolean = this.reversed,
        drawnCardIndex: Int? = this.drawnCardIndex,
        pendingSwapSeat: Int? = this.pendingSwapSeat,
        eliminatedSeats: Set<Int> = this.eliminatedSeats,
        removedSeats: Set<Int> = this.removedSeats,
    ): MonoChaosState = MonoChaosState(
        _gameId, _rules, _seats, _turn, hands, drawPile, discardPile,
        currentSeat, phase, topCardColor, turnCounter, monoCallWindowExpiry,
        monoWindowSeat, monoCalledSeat, lastActionSeat, lastActionCard,
        activeStackPenalty, stackTopValue, reversed, drawnCardIndex, pendingSwapSeat,
        eliminatedSeats, removedSeats,
    )
}
