package com.kglabs28.yapnplay.backend.games.base.rules

/**
 * How a game presents itself in game pickers (Game Zone carousel, room
 * lobby): three short taglines ("Match • Play • Win") and an art key the
 * app maps to its artwork. Plain data, so the server can list games too.
 */
data class GameShowcase(
    val taglines: List<String>,
    val artKey: String,
)
