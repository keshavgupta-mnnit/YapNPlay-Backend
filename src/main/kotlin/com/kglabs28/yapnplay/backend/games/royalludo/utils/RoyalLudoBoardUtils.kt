package com.kglabs28.yapnplay.backend.games.royalludo.utils

/**
 * Board geometry for Royal Ludo.
 *
 * The board has 52 shared squares (0-51) arranged clockwise.
 * Red starts at 0, Green at 13, Yellow at 26, Blue at 39.
 * Safe squares: the four start squares and the four star squares (8, 21, 34, 47).
 *
 * A piece's progress is counted from its own start square:
 * -1 = in base, 0-50 = shared track, 51-55 = private home column, 56 = finished.
 */
object RoyalLudoBoardUtils {

    /** Absolute track square for a piece at [progress] given its [startSquare]. */
    fun absoluteSquare(startSquare: Int, progress: Int): Int {
        require(progress >= 0) { "Progress must be >= 0" }
        require(progress <= PROGRESS_ENTER_HOME) { "Progress must be <= $PROGRESS_ENTER_HOME" }
        return (startSquare + progress) % TRACK_SIZE
    }

    /** Start square for a colour index (0 = red, 1 = green, 2 = yellow, 3 = blue). */
    fun startSquareFor(colour: Int): Int = when (colour) {
        0 -> RED_START
        1 -> GREEN_START
        2 -> YELLOW_START
        3 -> BLUE_START
        else -> error("Invalid colour: $colour")
    }

    /** True if [square] is safe (a start square or a star square). */
    fun isSafeSquare(square: Int): Boolean =
        square in STAR_SQUARES || square == RED_START || square == GREEN_START ||
            square == YELLOW_START || square == BLUE_START

    /** True if [progress] is in the private home column (51-55). */
    fun isInHomeColumn(progress: Int): Boolean = progress in 51..55

    /** True if [progress] is on the shared track (0-50). */
    fun isOnSharedTrack(progress: Int): Boolean = progress in 0..50
}
