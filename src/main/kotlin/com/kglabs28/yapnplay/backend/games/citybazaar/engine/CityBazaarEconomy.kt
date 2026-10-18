package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.HOTEL_LEVEL
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.RESALE_DIVISOR
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.SET_FOR_DOUBLE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TileKind

/** Rents, building rights and values. Pure functions over the state. */
object CityBazaarEconomy {

    /** How many tiles of [tile]'s colour [seat] owns. */
    fun colourCount(s: CityBazaarState, seat: Int, tile: Int): Int {
        val group = CityBazaarBoard.tile(tile).group?.takeIf { it.isColourSet } ?: return 0
        return CityBazaarBoard.groupTiles(group).count { s.owners[it] == seat }
    }

    /** 3 (or more) of a colour: that colour's rent doubles, whatever it is. */
    fun doubled(s: CityBazaarState, owner: Int, tile: Int): Boolean =
        s.cbRules.house.setDoubleRent && colourCount(s, owner, tile) >= SET_FOR_DOUBLE

    /** [seat] may build on [tile] (their city, not yet a hotel), ignoring money. */
    fun buildAllowed(s: CityBazaarState, seat: Int, tile: Int): Boolean =
        CityBazaarBoard.tile(tile).kind == TileKind.CITY && s.owners[tile] == seat && s.level(tile) < HOTEL_LEVEL

    /**
     * Rent for landing on [tile] now: its tier's rent (a company's pair rent
     * when the owner has its partner too), doubled with 3 of a colour,
     * halved while the owner sits it out in jail.
     */
    fun rent(s: CityBazaarState, tile: Int): Int {
        val owner = s.owners[tile] ?: return 0
        val spec = CityBazaarBoard.tile(tile)
        val base = spec.rents.getOrElse(CityBazaarBoard.tier(tile, s.level(tile)) { s.owners[it] }) { 0 }
        val withColour = if (doubled(s, owner, tile)) base * 2 else base
        return if (owner in s.halfRent) withColour / 2 else withColour
    }

    fun buildingResale(tile: Int): Int = CityBazaarBoard.tile(tile).buildCost / RESALE_DIVISOR

    fun tileResale(tile: Int): Int = CityBazaarBoard.tile(tile).price / RESALE_DIVISOR

    /** Everything [seat] could raise by selling to the bank. */
    fun liquidationValue(s: CityBazaarState, seat: Int): Int =
        s.owners.filter { it.value == seat }.keys.sumOf { tileResale(it) + s.level(it) * buildingResale(it) }

    /** Balance + tile prices + build cost of buildings (end-of-match ranking). */
    fun netWorth(s: CityBazaarState, seat: Int): Int =
        s.cashOf(seat) + s.owners.filter { it.value == seat }.keys.sumOf {
            CityBazaarBoard.tile(it).price + s.level(it) * CityBazaarBoard.tile(it).buildCost
        }

    fun properties(s: CityBazaarState, seat: Int): Int = s.owners.count { it.value == seat }

    /** Each house and each hotel counts as one building. */
    fun buildings(s: CityBazaarState, seat: Int): Int = houses(s, seat) + hotels(s, seat)

    fun houses(s: CityBazaarState, seat: Int): Int =
        s.owners.filter { it.value == seat }.keys.sumOf { s.level(it).let { l -> if (l in 1 until HOTEL_LEVEL) l else 0 } }

    fun hotels(s: CityBazaarState, seat: Int): Int =
        s.owners.filter { it.value == seat }.keys.count { s.level(it) == HOTEL_LEVEL }
}
