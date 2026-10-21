package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings

/**
 * XP and level, decided only by the server (values from the runtime
 * settings). Every finished match gives XP for playing plus a place bonus;
 * a player who left gets nothing. Every [GameSettings.XP_PER_LEVEL] XP is a
 * new level.
 */
class ProgressionPolicy(private val settings: Settings = Settings.Defaults) {

    fun xpFor(place: Int, left: Boolean): Int =
        if (left) 0 else settings[GameSettings.XP_PER_PLAY] + (settings[GameSettings.XP_PLACE_BONUS].getOrNull(place - 1) ?: 0)

    fun levelFor(xp: Int): Int = 1 + xp.coerceAtLeast(0) / settings[GameSettings.XP_PER_LEVEL]

    /** XP at which [level] starts (for the progress bar). */
    fun levelStartXp(level: Int): Int = (level - 1) * settings[GameSettings.XP_PER_LEVEL]
}
