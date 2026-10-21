package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import kotlinx.serialization.Serializable

@Serializable
data class MonoTurnState(
    override val seat: SeatNumber,
    override val phase: String,
) : TurnState

@Serializable
enum class MonoPhase {
    AWAITING_PLAY,
    AWAITING_COLOR_CHOICE,
    AWAITING_STACK_CHOICE,
    /** The Rule of 7 is waiting for its player to pick a partner. */
    AWAITING_SWAP_CHOICE,
    FINISHED,
}
