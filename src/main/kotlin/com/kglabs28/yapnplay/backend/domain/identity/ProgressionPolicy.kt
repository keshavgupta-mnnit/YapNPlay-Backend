package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings

/**
 * XP and level, decided only by the server (values from the runtime
 * settings). A finished match gives, added together:
 *  - a fixed XP for completing it ([GameSettings.XP_PER_PLAY]);
 *  - a fixed XP for the end position ([GameSettings.XP_PLACE_BONUS], 1st first);
 *  - a variable XP for its length: [GameSettings.XP_PER_MINUTE] for every
 *    whole minute, up to [GameSettings.XP_TIME_CAP_MINUTES].
 * A player who left gets nothing. Every [GameSettings.XP_PER_LEVEL] XP is a
 * new level.
 */
class ProgressionPolicy(private val settings: Settings = Settings.Defaults) {

    fun xpFor(place: Int, left: Boolean, durationMillis: Long): Int {
        if (left) return 0
        val completion = settings[GameSettings.XP_PER_PLAY]
        val position = settings[GameSettings.XP_PLACE_BONUS].getOrNull(place - 1) ?: 0
        val minutes = (durationMillis / MILLIS_PER_MINUTE).toInt().coerceIn(0, settings[GameSettings.XP_TIME_CAP_MINUTES])
        return completion + position + minutes * settings[GameSettings.XP_PER_MINUTE]
    }

    fun levelFor(xp: Int): Int = 1 + xp.coerceAtLeast(0) / settings[GameSettings.XP_PER_LEVEL]

    /** XP at which [level] starts (for the progress bar). */
    fun levelStartXp(level: Int): Int = (level - 1) * settings[GameSettings.XP_PER_LEVEL]

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
