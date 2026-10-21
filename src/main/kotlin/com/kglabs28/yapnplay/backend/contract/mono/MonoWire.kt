package com.kglabs28.yapnplay.backend.contract.mono

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════════════
// MONO (Standard) on the MATCH channel. Moves go in `GameAction.action`
// ({"type": …}); the table comes back as `MatchSnapshot.publicState`
// ([MonoPublicState]) and `MatchSnapshot.events` ([MonoWireEvent]).
// Card kinds: NumberCard, ReverseCard, SkipCard, DrawTwoCard,
// PrismColorWild, PrismDrawFour. Colours: RED, GREEN, BLUE, GOLD.
// ══════════════════════════════════════════════════════════════════════════

/** A card on the wire: a [kind] name plus the fields that kind needs. Prism cards carry no colour. */
@Serializable
data class MonoCardWire(
    val kind: String,
    val color: String? = null,
    val number: Int? = null,
)

/**
 * `MatchSnapshot.publicState` for MONO. [hands] holds only the viewer's
 * own cards; every seat's size comes from [cardCounts].
 * [monoCallWindowExpiry] is epoch millis; 0 when the window is shut.
 */
@Serializable
data class MonoPublicState(
    /** AWAITING_PLAY, AWAITING_COLOR_CHOICE, AWAITING_STACK_CHOICE, AWAITING_SWAP_CHOICE or FINISHED. */
    val phase: String,
    val topCard: MonoCardWire? = null,
    val topCardColor: String? = null,
    /** seat → that seat's cards. Only the viewer's own seat is ever sent. */
    val hands: Map<Int, List<MonoCardWire>> = emptyMap(),
    /** seat → how many cards they hold. Sent for every seat. */
    val cardCounts: Map<Int, Int> = emptyMap(),
    val drawPileCount: Int = 0,
    val discardPileCount: Int = 0,
    /** Penalty waiting to be stacked onto or accepted; 0 when none. */
    val activeStackPenalty: Int = 0,
    /** The value of the card that set the stack; a stacker must match it. */
    val stackTopValue: Int = 0,
    val monoCallWindowExpiry: Long = 0,
    /** The seat whose MONO! window is open. */
    val monoWindowSeat: Int? = null,
    /** The seat holding the MONO! shield. */
    val monoCalledSeat: Int? = null,
    /** Direction of play; true means counter-clockwise. */
    val reversed: Boolean = false,
    /** After a draw, the only card the seat may play. */
    val drawnCardIndex: Int? = null,
    /** Epoch millis when the drawn card can no longer be played (the turn passes); 0 when none. */
    val drawnCardExpiry: Long = 0,
    /** The seat that played a 7 and must pick a swap partner. */
    val pendingSwapSeat: Int? = null,
    val eliminatedSeats: List<Int> = emptyList(),
    val finishedSeats: List<Int> = emptyList(),
    /** Seats from first to last once the match is over. */
    val ranking: List<Int> = emptyList(),
)

/** MONO actions inside `GameAction.action`; `"type"` names the action. */
@Serializable
sealed interface MonoWireAction {
    @Serializable @SerialName("PlayCard") data class PlayCard(val handIndex: Int) : MonoWireAction
    @Serializable @SerialName("DrawCard") data object DrawCard : MonoWireAction
    /** [color] is RED, GREEN, BLUE or GOLD. */
    @Serializable @SerialName("ChooseColor") data class ChooseColor(val color: String) : MonoWireAction
    @Serializable @SerialName("CallMono") data object CallMono : MonoWireAction
    @Serializable @SerialName("AcceptStack") data object AcceptStack : MonoWireAction
    @Serializable @SerialName("Pass") data object Pass : MonoWireAction
    /** The Rule of 7 (room house rule): the seat whose hand to trade with. */
    @Serializable @SerialName("ChooseSwapTarget") data class ChooseSwapTarget(val seat: Int) : MonoWireAction
}

/** MONO events in `MatchSnapshot.events`; `"type"` names the event. */
@Serializable
sealed interface MonoWireEvent {
    @Serializable @SerialName("CardPlayed")
    data class CardPlayed(val seat: Int, val card: MonoCardWire, val color: String? = null) : MonoWireEvent

    /** Sent only to the seat that drew; everyone else gets [CardsDrawn]. */
    @Serializable @SerialName("CardDrawn")
    data class CardDrawn(val seat: Int, val card: MonoCardWire) : MonoWireEvent

    /** 0.5.0: someone else drew [count] cards (which ones stays hidden). */
    @Serializable @SerialName("CardsDrawn")
    data class CardsDrawn(val seat: Int, val count: Int) : MonoWireEvent

    @Serializable @SerialName("ColorChosen")
    data class ColorChosen(val seat: Int, val color: String) : MonoWireEvent

    @Serializable @SerialName("MonoCalled")
    data class MonoCalled(val seat: Int) : MonoWireEvent

    @Serializable @SerialName("MonoMissed")
    data class MonoMissed(val seat: Int, val penaltyCards: Int) : MonoWireEvent

    @Serializable @SerialName("HandSwapped")
    data class HandSwapped(val from: Int, val to: Int) : MonoWireEvent

    @Serializable @SerialName("HandsPassed")
    data class HandsPassed(val direction: String) : MonoWireEvent

    @Serializable @SerialName("TurnPassed")
    data class TurnPassed(val seat: Int) : MonoWireEvent

    @Serializable @SerialName("SeatFinished")
    data class SeatFinished(val seat: Int, val place: Int) : MonoWireEvent

    @Serializable @SerialName("MatchEnded")
    data class MatchEnded(val finishingOrder: List<Int>) : MonoWireEvent
}

// ══════════════════════════════════════════════════════════════════════════
// MONO: Complete Chaos. Same shapes, plus stacking, the Roulette and the
// 25-card knockout. Extra card kinds: ColoredDrawFourCard, DiscardAllCard,
// SkipEveryoneCard, PrismReverseDrawFour, PrismDrawSix, PrismDrawTen,
// PrismColorRoulette.
// ══════════════════════════════════════════════════════════════════════════

@Serializable
data class MonoChaosCardWire(
    val kind: String,
    val color: String? = null,
    val number: Int? = null,
)

@Serializable
data class MonoChaosPublicState(
    /** AWAITING_PLAY, AWAITING_COLOR_CHOICE, AWAITING_STACK_CHOICE, AWAITING_ROULETTE_COLOR, AWAITING_SWAP_CHOICE or FINISHED. */
    val phase: String,
    val topCard: MonoChaosCardWire? = null,
    val topCardColor: String? = null,
    val hands: Map<Int, List<MonoChaosCardWire>> = emptyMap(),
    val cardCounts: Map<Int, Int> = emptyMap(),
    val drawPileCount: Int = 0,
    val discardPileCount: Int = 0,
    val activeStackPenalty: Int = 0,
    val monoCallWindowExpiry: Long = 0,
    val monoWindowSeat: Int? = null,
    val monoCalledSeat: Int? = null,
    /** The value of the card that set the stack; a stacker must equal or beat it (any colour). */
    val stackTopValue: Int = 0,
    val reversed: Boolean = false,
    val drawnCardIndex: Int? = null,
    val pendingSwapSeat: Int? = null,
    /** Seats knocked out at the 25-card threshold. */
    val eliminatedSeats: List<Int> = emptyList(),
    val finishedSeats: List<Int> = emptyList(),
    val ranking: List<Int> = emptyList(),
    /** 0.5.0: the hand size that knocks a player out (an admin setting, 25 by default). */
    val knockoutThreshold: Int = 25,
)

@Serializable
sealed interface MonoChaosWireAction {
    @Serializable @SerialName("PlayCard") data class PlayCard(val handIndex: Int) : MonoChaosWireAction
    @Serializable @SerialName("DrawUntilPlayable") data object DrawUntilPlayable : MonoChaosWireAction
    @Serializable @SerialName("ChooseColor") data class ChooseColor(val color: String) : MonoChaosWireAction
    @Serializable @SerialName("ChooseRouletteColor") data class ChooseRouletteColor(val color: String) : MonoChaosWireAction
    @Serializable @SerialName("CallMono") data object CallMono : MonoChaosWireAction
    @Serializable @SerialName("StackDraw") data class StackDraw(val handIndex: Int) : MonoChaosWireAction
    @Serializable @SerialName("AcceptStack") data object AcceptStack : MonoChaosWireAction
    @Serializable @SerialName("Pass") data object Pass : MonoChaosWireAction
    @Serializable @SerialName("ChooseSwapTarget") data class ChooseSwapTarget(val seat: Int) : MonoChaosWireAction
}

@Serializable
sealed interface MonoChaosWireEvent {
    @Serializable @SerialName("CardPlayed")
    data class CardPlayed(val seat: Int, val card: MonoChaosCardWire, val color: String? = null) : MonoChaosWireEvent

    @Serializable @SerialName("CardDrawn")
    data class CardDrawn(val seat: Int, val card: MonoChaosCardWire) : MonoChaosWireEvent

    @Serializable @SerialName("CardsDrawn")
    data class CardsDrawn(val seat: Int, val count: Int) : MonoChaosWireEvent

    /** 0.5.0: Discard All took [count] more cards of that colour out of the hand. */
    @Serializable @SerialName("CardsDiscarded")
    data class CardsDiscarded(val seat: Int, val count: Int) : MonoChaosWireEvent

    @Serializable @SerialName("ColorChosen")
    data class ColorChosen(val seat: Int, val color: String) : MonoChaosWireEvent

    @Serializable @SerialName("RouletteColorChosen")
    data class RouletteColorChosen(val victim: Int, val color: String) : MonoChaosWireEvent

    @Serializable @SerialName("StackDraw")
    data class StackDraw(val seat: Int, val card: MonoChaosCardWire, val newStackTotal: Int) : MonoChaosWireEvent

    @Serializable @SerialName("StackAccepted")
    data class StackAccepted(val seat: Int, val penaltyCards: Int) : MonoChaosWireEvent

    @Serializable @SerialName("MonoCalled")
    data class MonoCalled(val seat: Int) : MonoChaosWireEvent

    @Serializable @SerialName("MonoMissed")
    data class MonoMissed(val seat: Int, val penaltyCards: Int) : MonoChaosWireEvent

    @Serializable @SerialName("HandSwapped")
    data class HandSwapped(val from: Int, val to: Int) : MonoChaosWireEvent

    @Serializable @SerialName("HandsPassed")
    data class HandsPassed(val direction: String) : MonoChaosWireEvent

    @Serializable @SerialName("PlayerEliminated")
    data class PlayerEliminated(val seat: Int) : MonoChaosWireEvent

    @Serializable @SerialName("TurnPassed")
    data class TurnPassed(val seat: Int) : MonoChaosWireEvent

    @Serializable @SerialName("SeatFinished")
    data class SeatFinished(val seat: Int, val place: Int) : MonoChaosWireEvent

    @Serializable @SerialName("MatchEnded")
    data class MatchEnded(val finishingOrder: List<Int>) : MonoChaosWireEvent
}
