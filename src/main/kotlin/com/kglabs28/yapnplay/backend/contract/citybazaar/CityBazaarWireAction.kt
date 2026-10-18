package com.kglabs28.yapnplay.backend.contract.citybazaar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** City Bazaar actions inside `GameAction.action`; `"type"` names the action. */
@Serializable
sealed interface CityBazaarWireAction {
    @Serializable @SerialName("RollDice") data object Roll : CityBazaarWireAction
    @Serializable @SerialName("BuyTile") data object Buy : CityBazaarWireAction
    @Serializable @SerialName("DeclineTile") data object Decline : CityBazaarWireAction
    @Serializable @SerialName("BuildOnTile") data class Build(val tile: Int) : CityBazaarWireAction
    @Serializable @SerialName("SkipBuild") data object SkipBuild : CityBazaarWireAction
    @Serializable @SerialName("SellBuilding") data class SellBuilding(val tile: Int) : CityBazaarWireAction
    @Serializable @SerialName("SellTile") data class SellTile(val tile: Int) : CityBazaarWireAction
    @Serializable @SerialName("StartAuction") data class StartAuction(val tile: Int) : CityBazaarWireAction
    @Serializable @SerialName("PlaceBid") data object Bid : CityBazaarWireAction
    @Serializable @SerialName("PayJailFine") data object PayJailFine : CityBazaarWireAction
    @Serializable @SerialName("StayInJail") data object StayInJail : CityBazaarWireAction
    @Serializable @SerialName("DeclareBankruptcy") data object DeclareBankruptcy : CityBazaarWireAction
}
