package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * XP and level, decided only by the server. Every finished match gives XP in
 * hundreds: 100 for playing, plus 300 / 200 / 100 for 1st / 2nd / 3rd; a
 * player who left gets nothing. Every 1,000 XP is a new level.
 */
class ProgressionPolicy {

    fun xpFor(place: Int, left: Boolean): Int =
        if (left) 0 else BackendConstants.XP_FOR_PLAYING + (PLACE_BONUS.getOrNull(place - 1) ?: 0)

    fun levelFor(xp: Int): Int = 1 + xp.coerceAtLeast(0) / BackendConstants.XP_PER_LEVEL

    /** XP at which [level] starts (for the progress bar). */
    fun levelStartXp(level: Int): Int = (level - 1) * BackendConstants.XP_PER_LEVEL

    private companion object {
        val PLACE_BONUS = listOf(300, 200, 100)
    }
}
