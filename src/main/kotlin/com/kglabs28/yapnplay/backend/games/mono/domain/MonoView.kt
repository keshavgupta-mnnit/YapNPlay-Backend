package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import kotlinx.serialization.Serializable

/**
 * What one viewer sees of a Standard MONO table. A spectator gets the
 * public table only; a player also gets their own [hand]. Never send
 * another player's cards and hide them in the app.
 */
@Serializable
data class MonoView(
    val seats: List<Int>,
    val currentSeat: Int,
    val phase: String,
    val topCard: MonoCard?,
    val topCardColor: MonoConstants.Color?,
    val hand: List<MonoCard>,
    val opponentCardCounts: Map<Int, Int>,
    val drawPileCount: Int,
    val discardPileCount: Int,
    /** Penalty waiting to be stacked onto or taken; 0 when none. */
    val activeStackPenalty: Int = 0,
    val eliminatedSeats: Set<Int> = emptySet(),
    /** Deadline for the MONO! callout; 0 when no window is open. */
    val monoCallWindowExpiry: Long = 0,
    /** The seat whose MONO! window is open. */
    val monoWindowSeat: Int? = null,
    /** The seat holding the MONO! shield. */
    val monoCalledSeat: Int? = null,
    /** Direction of play; true means counter-clockwise. */
    val reversed: Boolean = false,
    /** After a draw, the only card the seat may play. */
    val drawnCardIndex: Int? = null,
    /** The seat that played a 7 and must pick a swap partner. */
) : GameView