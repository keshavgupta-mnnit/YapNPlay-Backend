package com.kglabs28.yapnplay.backend.contract.royalludo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Royal Ludo events in `MatchSnapshot.events`; `"type"` names the event. */
@Serializable
sealed interface RoyalLudoWireEvent {

    @Serializable
    @SerialName("Rolled")
    data class Rolled(val seat: Int, val value: Int) : RoyalLudoWireEvent

    @Serializable
    @SerialName("Moved")
    data class Moved(val seat: Int, val piece: Int, val from: Int, val to: Int) : RoyalLudoWireEvent

    @Serializable
    @SerialName("Captured")
    data class Captured(val seat: Int, val victimSeat: Int, val victimPiece: Int) : RoyalLudoWireEvent

    @Serializable
    @SerialName("TurnPassed")
    data class TurnPassed(val seat: Int) : RoyalLudoWireEvent

    /** A third six: every piece goes back to [pieces] (seat → progress). */
    @Serializable
    @SerialName("Reverted")
    data class Reverted(val seat: Int, val pieces: Map<Int, List<Int>>) : RoyalLudoWireEvent
}
