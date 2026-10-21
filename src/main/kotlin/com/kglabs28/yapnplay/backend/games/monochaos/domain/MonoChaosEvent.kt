package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import kotlinx.serialization.Serializable

@Serializable
sealed class MonoChaosEvent : GameEvent {
    data class CardPlayed(val seat: SeatNumber, val card: MonoChaosCard, val color: MonoChaosConstants.Color?) : MonoChaosEvent()
    data class CardDrawn(val seat: SeatNumber, val card: MonoChaosCard) : MonoChaosEvent()
    data class CardsDrawn(val seat: SeatNumber, val count: Int) : MonoChaosEvent()
    /** Discard All: [count] more cards of that colour left the hand with it. */
    data class CardsDiscarded(val seat: SeatNumber, val count: Int) : MonoChaosEvent()
    data class ColorChosen(val seat: SeatNumber, val color: MonoChaosConstants.Color) : MonoChaosEvent()
    data class RouletteColorChosen(val victim: SeatNumber, val color: MonoChaosConstants.Color) : MonoChaosEvent()
    data class StackDraw(val seat: SeatNumber, val card: MonoChaosCard, val newStackTotal: Int) : MonoChaosEvent()
    data class StackAccepted(val seat: SeatNumber, val penaltyCards: Int) : MonoChaosEvent()
    data class MonoCalled(val seat: SeatNumber) : MonoChaosEvent()
    data class MonoMissed(val seat: SeatNumber, val penaltyCards: Int) : MonoChaosEvent()
    data class HandSwapped(val from: SeatNumber, val to: SeatNumber) : MonoChaosEvent()
    data class HandsPassed(val direction: String) : MonoChaosEvent()
    data class PlayerEliminated(val seat: SeatNumber) : MonoChaosEvent()
    data class TurnPassed(val seat: SeatNumber) : MonoChaosEvent()
    data class SeatFinished(val seat: SeatNumber, val place: Int) : MonoChaosEvent()
    data class MatchEnded(val finishingOrder: List<Int>) : MonoChaosEvent()
}
