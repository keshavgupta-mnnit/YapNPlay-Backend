package com.kglabs28.yapnplay.backend.contract.royalludo

import com.kglabs28.yapnplay.backend.contract.Colour

/**
 * Each arm's colour, clockwise from arm 0, for a board of 4, 6 or 8 arms (the app draws the same
 * order). A seat's colour names its arm. The square board keeps its four classic colours.
 */
object RoyalLudoArmColours {
    private val SQUARE = listOf(Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE)
    private val HEXAGON = listOf(Colour.RED, Colour.ORANGE, Colour.GREEN, Colour.YELLOW, Colour.BLUE, Colour.PURPLE)
    private val OCTAGON = listOf(Colour.RED, Colour.ORANGE, Colour.GREEN, Colour.TEAL, Colour.YELLOW, Colour.PINK, Colour.BLUE, Colour.PURPLE)

    fun of(arms: Int): List<Colour> = when (arms) {
        HEXAGON.size -> HEXAGON
        OCTAGON.size -> OCTAGON
        else -> SQUARE
    }
}
