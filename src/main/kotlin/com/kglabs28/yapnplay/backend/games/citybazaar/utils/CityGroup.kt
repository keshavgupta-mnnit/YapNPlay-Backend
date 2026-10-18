package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/**
 * The original game's card colours: four city colours of 5 cities each,
 * and the grey company cards, which aren't a colour set (they pay more in
 * pairs instead).
 */
enum class CityGroup {
    RED, BLUE, GREEN, YELLOW, GREY;

    /** 3 of a colour doubles its rent; companies don't count. */
    val isColourSet: Boolean get() = this != GREY
}
