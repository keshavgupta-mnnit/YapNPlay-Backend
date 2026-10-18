package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/**
 * One board tile. [rents] for a city: land, 1, 2, 3 houses, hotel; for a
 * company: alone, and when the same player also owns its [partner].
 * [buildCost] is the price of one house, and of the hotel. A tax tile
 * charges [taxPerProperty] for each property the player owns (Income Tax)
 * or [taxPerBuilding] for each building (Wealth Tax).
 */
data class TileSpec(
    val index: Int,
    val name: String,
    val kind: TileKind,
    val group: CityGroup? = null,
    val price: Int = 0,
    val rents: List<Int> = emptyList(),
    val buildCost: Int = 0,
    val partner: Int? = null,
    val taxPerProperty: Int = 0,
    val taxPerBuilding: Int = 0,
) {
    val buyable: Boolean get() = kind == TileKind.CITY || kind == TileKind.COMPANY
}
