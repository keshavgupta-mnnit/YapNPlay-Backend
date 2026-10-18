package com.kglabs28.yapnplay.backend.games.base.rules

import kotlinx.serialization.Serializable

/**
 * How public Quick Play (random players) fills a match for this game.
 * A public match always has exactly [seats] players: the server searches
 * for up to [searchSeconds]; the match starts early as soon as [seats]
 * humans are found; when time runs out the empty seats are filled with
 * CPU players (engine autoplay) if [fillWithCpu].
 *
 * [seats] is separate from min/max players: a 4–8 player game may run
 * random matches at 4 seats while rooms allow up to 8.
 * Rooms never use this — see the room's bot-fill option instead.
 */
@Serializable
data class MatchmakingConfig(
    val seats: Int,
    val searchSeconds: Long = 30,
    val fillWithCpu: Boolean = true,
)
