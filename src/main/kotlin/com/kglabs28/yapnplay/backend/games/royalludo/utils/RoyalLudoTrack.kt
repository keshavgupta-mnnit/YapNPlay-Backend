package com.kglabs28.yapnplay.backend.games.royalludo.utils

import kotlinx.serialization.Serializable

/**
 * The board's track for [arms] arms (4: the square board, 6: hexagon, 8: octagon). Every arm has
 * [ARM_SQUARES] shared squares; colour `c` (arm `c`, clockwise) starts at `c × 13` and its star is
 * 8 squares on. A piece's progress counts from its own start: -1 in base, 0 … [enterHome] on the
 * shared track, then the [HOME_COLUMN_LENGTH] squares of its home column, [finish] home. On the
 * square board this is the classic 52-square track (finish 56).
 */
@Serializable
data class RoyalLudoTrack(val arms: Int = SQUARE_ARMS) {
    /** Shared squares round the board. */
    val size: Int get() = ARM_SQUARES * arms

    /** The last shared-track progress before the home column. */
    val enterHome: Int get() = size - 2

    /** Progress of a piece that is home. */
    val finish: Int get() = size - 1 + HOME_COLUMN_LENGTH

    /** Colour [colour]'s start square. */
    fun start(colour: Int): Int {
        require(colour in 0 until arms) { "Invalid colour: $colour" }
        return colour * ARM_SQUARES
    }

    /** The shared square a piece of the colour starting at [startSquare] stands on at [progress]. */
    fun square(startSquare: Int, progress: Int): Int {
        require(progress in 0..enterHome) { "Progress must be 0..$enterHome" }
        return (startSquare + progress) % size
    }

    /** A start square or a star square: nobody is captured there. */
    fun isSafe(square: Int): Boolean = square % ARM_SQUARES == 0 || square % ARM_SQUARES == STAR_OFFSET

    fun isInHomeColumn(progress: Int): Boolean = progress in enterHome + 1 until finish

    fun isOnSharedTrack(progress: Int): Boolean = progress in 0..enterHome

    companion object {
        const val SQUARE_ARMS = 4
        const val HEXAGON_ARMS = 6
        const val OCTAGON_ARMS = 8

        /** The board for [players]: up to 4 the square board, 5–6 the hexagon, 7–8 the octagon. */
        fun forPlayers(players: Int): RoyalLudoTrack = RoyalLudoTrack(
            when {
                players <= SQUARE_ARMS -> SQUARE_ARMS
                players <= HEXAGON_ARMS -> HEXAGON_ARMS
                else -> OCTAGON_ARMS
            },
        )
    }
}

/** Shared squares on each arm of the track. */
const val ARM_SQUARES = 13

/** A colour's star square, this many squares past its start. */
const val STAR_OFFSET = 8
