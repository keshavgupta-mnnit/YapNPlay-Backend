package com.kglabs28.yapnplay.backend.games.citybazaar.engine

/** [seller]'s [tile] is up for auction: [bid] is the highest so far by [leader] (null: nobody yet; it opens at [bid]). */
data class CityBazaarAuction(val tile: Int, val seller: Int, val bid: Int, val leader: Int?)
