package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import kotlinx.serialization.Serializable

/**
 * What one viewer sees of a MONO: Complete Chaos table. A spectator gets
 * the public table only; a player also gets their own [hand]. Never send
 * another player's cards and hide them in the app.
 */
@Serializable
data class MonoChaosView(
    val seats: List<Int>,
    val currentSeat: Int,
    val phase: String,
    val topCard: MonoChaosCard?,
    val topCardColor: MonoChaosConstants.Color?,
    val hand: List<MonoChaosCard>,
    val opponentCardCounts: Map<Int, Int>,
    val drawPileCount: Int,
    val discardPileCount: Int,
    /** Deadline for the MONO! callout; 0 when the window is shut. */
    val monoCallWindowExpiry: Long = 0,
    /** Penalty waiting to be stacked onto or taken; 0 when none. */
    val activeStackPenalty: Int = 0,
    val eliminatedSeats: Set<Int> = emptySet(),
    /** The seat whose MONO! window is open. */
    val monoWindowSeat: Int? = null,
    /** The seat holding the MONO! shield. */
    val monoCalledSeat: Int? = null,
    /** The value of the card that set the stack; a stacker must match it. */
    val stackTopValue: Int = 0,
    /** Direction of play; true means counter-clockwise. */
    val reversed: Boolean = false,
    /** After a draw, the only card the seat may play. */
    val drawnCardIndex: Int? = null,
    /** The seat that played a 7 and must pick a swap partner. */
    val pendingSwapSeat: Int? = null,
) : GameView