package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import kotlinx.serialization.Serializable

@Serializable
sealed class MonoEvent : GameEvent {
    data class CardPlayed(val seat: SeatNumber, val card: MonoCard, val color: MonoConstants.Color?) : MonoEvent()
    data class CardDrawn(val seat: SeatNumber, val card: MonoCard) : MonoEvent()
    data class ColorChosen(val seat: SeatNumber, val color: MonoConstants.Color) : MonoEvent()
    data class MonoCalled(val seat: SeatNumber) : MonoEvent()
    data class MonoMissed(val seat: SeatNumber, val penaltyCards: Int) : MonoEvent()
    /** [catcher] caught [seat] without the MONO! shield: [seat] drew [penaltyCards]. */
    data class MonoCaught(val catcher: SeatNumber, val seat: SeatNumber, val penaltyCards: Int) : MonoEvent()
    /** [seat] pressed Caught on [target], who had called: [seat] drew [penaltyCards]. */
    data class FalseCatch(val seat: SeatNumber, val target: SeatNumber, val penaltyCards: Int) : MonoEvent()
    /** The Rule of 7: two seats traded hands. */
    data class HandSwapped(val from: SeatNumber, val to: SeatNumber) : MonoEvent()
    /** The Rule of 0: every hand moved one way around the table. */
    data class HandsPassed(val direction: String) : MonoEvent()
    data class TurnPassed(val seat: SeatNumber) : MonoEvent()
    data class SeatFinished(val seat: SeatNumber, val place: Int) : MonoEvent()
    data class MatchEnded(val finishingOrder: List<Int>) : MonoEvent()
}
