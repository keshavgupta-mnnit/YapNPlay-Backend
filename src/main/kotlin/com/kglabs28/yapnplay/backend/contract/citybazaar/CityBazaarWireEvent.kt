package com.kglabs28.yapnplay.backend.contract.citybazaar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** City Bazaar events in `MatchSnapshot.events`; `"type"` names the event. */
@Serializable
sealed interface CityBazaarWireEvent {

    @Serializable @SerialName("Rolled")
    data class Rolled(val seat: Int, val die1: Int, val die2: Int) : CityBazaarWireEvent

    /** [steps] negative going back; [jump] straight to jail. */
    @Serializable @SerialName("Moved")
    data class Moved(val seat: Int, val from: Int, val to: Int, val steps: Int, val jump: Boolean) : CityBazaarWireEvent

    /** [from] / [to] null = the bank. [reason] is a MoneyReason name. */
    @Serializable @SerialName("Money")
    data class Money(val from: Int? = null, val to: Int? = null, val amount: Int, val reason: String, val tile: Int? = null) : CityBazaarWireEvent

    @Serializable @SerialName("Card")
    data class Card(val seat: Int, val cardId: String) : CityBazaarWireEvent

    @Serializable @SerialName("Bought")
    data class Bought(val seat: Int, val tile: Int) : CityBazaarWireEvent

    @Serializable @SerialName("CantAfford")
    data class CantAfford(val seat: Int, val tile: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Built")
    data class Built(val seat: Int, val tile: Int, val level: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Sold")
    data class Sold(val seat: Int, val tile: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Jailed")
    data class Jailed(val seat: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Freed")
    data class Freed(val seat: Int) : CityBazaarWireEvent

    @Serializable @SerialName("HalfRent")
    data class HalfRent(val seat: Int) : CityBazaarWireEvent

    @Serializable @SerialName("AuctionStarted")
    data class AuctionStarted(val seller: Int, val tile: Int, val opening: Int) : CityBazaarWireEvent

    @Serializable @SerialName("BidPlaced")
    data class BidPlaced(val seat: Int, val tile: Int, val amount: Int) : CityBazaarWireEvent

    /** [winner] null: nobody bid, the bank bought it. */
    @Serializable @SerialName("AuctionEnded")
    data class AuctionEnded(val seller: Int, val tile: Int, val winner: Int? = null, val amount: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Bankrupt")
    data class Bankrupt(val seat: Int) : CityBazaarWireEvent

    @Serializable @SerialName("Skipped")
    data class Skipped(val seat: Int) : CityBazaarWireEvent

    @Serializable @SerialName("TurnPassed")
    data class TurnPassed(val seat: Int) : CityBazaarWireEvent
}
