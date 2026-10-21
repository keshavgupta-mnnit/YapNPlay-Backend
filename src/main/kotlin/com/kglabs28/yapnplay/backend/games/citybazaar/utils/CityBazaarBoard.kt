package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/**
 * The 36 tiles of the original "Business" board, clockwise from START
 * (docs/city-bazaar-rules.md §2). Companies pay more in pairs: Water Works
 * with Air India, Railways with B.E.S.T., Electric Company with Motorboat.
 */
object CityBazaarBoard {
    val tiles: List<TileSpec> = listOf(
        TileSpec(0, "START", TileKind.START),
        TileSpec(1, "Mumbai", TileKind.CITY, CityGroup.RED, 8500, listOf(1200, 4000, 5700, 7300, 10000), 7000),
        TileSpec(2, "Water Works", TileKind.COMPANY, CityGroup.GREY, 3200, listOf(500, 1000), partner = 24),
        TileSpec(3, "Railways", TileKind.COMPANY, CityGroup.GREY, 9500, listOf(1000, 1350), partner = 13),
        TileSpec(4, "Ahmedabad", TileKind.CITY, CityGroup.RED, 4000, listOf(500, 1500, 3000, 4500, 6000), 2000),
        TileSpec(5, "Income Tax", TileKind.TAX, taxPerProperty = PROPERTY_TAX_PER),
        TileSpec(6, "Indore", TileKind.CITY, CityGroup.BLUE, 1500, listOf(200, 600, 1200, 1800, 2500), 1000),
        TileSpec(7, "Luck", TileKind.LUCK),
        TileSpec(8, "Jaipur", TileKind.CITY, CityGroup.BLUE, 3000, listOf(400, 1200, 2400, 3600, 5000), 1500),
        TileSpec(9, "Jail", TileKind.JAIL),
        TileSpec(10, "Delhi", TileKind.CITY, CityGroup.GREEN, 6000, listOf(750, 3000, 4300, 5500, 7500), 5000),
        TileSpec(11, "Chandigarh", TileKind.CITY, CityGroup.GREEN, 2500, listOf(200, 900, 1600, 2500, 3500), 3000),
        TileSpec(12, "Electric Company", TileKind.COMPANY, CityGroup.GREY, 2500, listOf(400, 800), partner = 34),
        TileSpec(13, "B.E.S.T.", TileKind.COMPANY, CityGroup.GREY, 3500, listOf(600, 1100), partner = 3),
        TileSpec(14, "Shimla", TileKind.CITY, CityGroup.YELLOW, 2200, listOf(250, 900, 1800, 2700, 3600), 1000),
        TileSpec(15, "Amritsar", TileKind.CITY, CityGroup.YELLOW, 3300, listOf(300, 1350, 2400, 3750, 5250), 3000),
        TileSpec(16, "Shagun Chest", TileKind.CHEST),
        TileSpec(17, "Srinagar", TileKind.CITY, CityGroup.YELLOW, 5000, listOf(550, 3500, 5000, 7000, 8000), 6000),
        TileSpec(18, "Club", TileKind.CLUB),
        TileSpec(19, "Agra", TileKind.CITY, CityGroup.BLUE, 2500, listOf(350, 1000, 2000, 3000, 4000), 1000),
        TileSpec(20, "Luck", TileKind.LUCK),
        TileSpec(21, "Kanpur", TileKind.CITY, CityGroup.BLUE, 4000, listOf(500, 1500, 3000, 4500, 6000), 2000),
        TileSpec(22, "Patna", TileKind.CITY, CityGroup.BLUE, 2000, listOf(250, 800, 1600, 2400, 3200), 1000),
        TileSpec(23, "Darjeeling", TileKind.CITY, CityGroup.RED, 2500, listOf(200, 1200, 2600, 3500, 5000), 3000),
        TileSpec(24, "Air India", TileKind.COMPANY, CityGroup.GREY, 10500, listOf(1200, 2000), partner = 2),
        TileSpec(25, "Kolkata", TileKind.CITY, CityGroup.RED, 6500, listOf(850, 2400, 4800, 7200, 9500), 3000),
        TileSpec(26, "Hyderabad", TileKind.CITY, CityGroup.RED, 3500, listOf(450, 1400, 2800, 4200, 5500), 1500),
        TileSpec(27, "Rest House", TileKind.REST_HOUSE),
        TileSpec(28, "Chennai", TileKind.CITY, CityGroup.YELLOW, 7000, listOf(900, 2600, 5200, 7800, 10000), 3500),
        TileSpec(29, "Shagun Chest", TileKind.CHEST),
        TileSpec(30, "Bengaluru", TileKind.CITY, CityGroup.YELLOW, 4000, listOf(500, 1500, 3000, 4500, 6000), 2000),
        TileSpec(31, "Wealth Tax", TileKind.TAX, taxPerBuilding = WEALTH_TAX_PER_BUILDING),
        TileSpec(32, "Ooty", TileKind.CITY, CityGroup.GREEN, 2500, listOf(350, 1000, 2000, 3000, 4000), 1000),
        TileSpec(33, "Kochi", TileKind.CITY, CityGroup.GREEN, 3000, listOf(400, 1200, 2400, 3600, 5000), 1500),
        TileSpec(34, "Motorboat", TileKind.COMPANY, CityGroup.GREY, 3000, listOf(450, 900), partner = 12),
        TileSpec(35, "Goa", TileKind.CITY, CityGroup.GREEN, 4000, listOf(500, 2000, 3500, 5000, 6500), 1500),
    )

    fun tile(index: Int): TileSpec = tiles[index]

    fun groupTiles(group: CityGroup): List<Int> = tiles.filter { it.group == group }.map { it.index }

    fun tilesOf(kind: TileKind): List<Int> = tiles.filter { it.kind == kind }.map { it.index }

    /**
     * Rent tier of [tile] for its owner: a city's buildings ([level]), or
     * for a company 1 when [ownerOf] its partner is the same player.
     */
    fun tier(tile: Int, level: Int, ownerOf: (Int) -> Int?): Int {
        val spec = tile(tile)
        return when (spec.kind) {
            TileKind.CITY -> level
            TileKind.COMPANY -> if (spec.partner != null && ownerOf(spec.partner) != null && ownerOf(spec.partner) == ownerOf(tile)) 1 else 0
            else -> 0
        }
    }
}
