package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

import com.kglabs28.yapnplay.backend.games.citybazaar.engine.RestTaken
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.AuctionEnded
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.AuctionStarted
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BidPlaced
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BuildingBuilt
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BuildingSold
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CantAfford
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CardDrawn
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.DiceRolled
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.HalfRentTaken
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.LeftJail
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.MoneyMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.SentToJail
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TileBought
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TileSold
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TokenMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TurnPassed
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TurnSkipped
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.WentBankrupt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** City Bazaar engine events → wire JSON; internal events (debt, rest, match end) are dropped. */
class CityBazaarWireMapper(private val json: Json) {

    fun toWire(events: List<GameEvent>): List<JsonObject> = events.mapNotNull { event ->
        when (event) {
            is DiceRolled -> CityBazaarWireEvent.Rolled(event.seat, event.die1, event.die2)
            is TokenMoved -> CityBazaarWireEvent.Moved(event.seat, event.from, event.to, event.steps, event.jump)
            is MoneyMoved -> CityBazaarWireEvent.Money(event.from, event.to, event.amount, event.reason.name, event.tile)
            is CardDrawn -> CityBazaarWireEvent.Card(event.seat, event.cardId)
            is TileBought -> CityBazaarWireEvent.Bought(event.seat, event.tile)
            is CantAfford -> CityBazaarWireEvent.CantAfford(event.seat, event.tile)
            is BuildingBuilt -> CityBazaarWireEvent.Built(event.seat, event.tile, event.level)
            is BuildingSold -> CityBazaarWireEvent.Sold(event.seat, event.tile)
            is TileSold -> CityBazaarWireEvent.Sold(event.seat, event.tile)
            is SentToJail -> CityBazaarWireEvent.Jailed(event.seat)
            is LeftJail -> CityBazaarWireEvent.Freed(event.seat)
            is HalfRentTaken -> CityBazaarWireEvent.HalfRent(event.seat)
            is AuctionStarted -> CityBazaarWireEvent.AuctionStarted(event.seller, event.tile, event.opening)
            is BidPlaced -> CityBazaarWireEvent.BidPlaced(event.seat, event.tile, event.amount)
            is AuctionEnded -> CityBazaarWireEvent.AuctionEnded(event.seller, event.tile, event.winner, event.amount)
            is WentBankrupt -> CityBazaarWireEvent.Bankrupt(event.seat)
            is TurnSkipped -> CityBazaarWireEvent.Skipped(event.seat)
            is RestTaken -> CityBazaarWireEvent.Rested(event.seat)
            is TurnPassed -> CityBazaarWireEvent.TurnPassed(event.seat)
            else -> null
        }?.let { json.encodeToJsonElement(CityBazaarWireEvent.serializer(), it).jsonObject }
    }
}
