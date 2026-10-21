package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import kotlin.math.roundToInt

/**
 * XP and level, decided only by the server (values from the runtime
 * settings). Every finished match gives XP for playing plus a place bonus,
 * scaled by how long it ran: a match of [GameSettings.XP_REFERENCE_MINUTES]
 * gives the full amount, shorter ones less and longer ones more (within the
 * min/max factor). XP comes in hundreds, at least 100 for finishing; a
 * player who left gets nothing. Every [GameSettings.XP_PER_LEVEL] XP is a
 * new level.
 */
class ProgressionPolicy(private val settings: Settings = Settings.Defaults) {

    fun xpFor(place: Int, left: Boolean, durationMillis: Long = referenceMillis()): Int {
        if (left) return 0
        val base = settings[GameSettings.XP_PER_PLAY] + (settings[GameSettings.XP_PLACE_BONUS].getOrNull(place - 1) ?: 0)
        if (base <= 0) return 0
        val factor = (durationMillis.toDouble() / referenceMillis())
            .coerceIn(settings[GameSettings.XP_MIN_TIME_FACTOR_PCT] / PERCENT, settings[GameSettings.XP_MAX_TIME_FACTOR_PCT] / PERCENT)
        return ((base * factor / XP_STEP).roundToInt() * XP_STEP).coerceAtLeast(XP_STEP)
    }

    fun levelFor(xp: Int): Int = 1 + xp.coerceAtLeast(0) / settings[GameSettings.XP_PER_LEVEL]

    /** XP at which [level] starts (for the progress bar). */
    fun levelStartXp(level: Int): Int = (level - 1) * settings[GameSettings.XP_PER_LEVEL]

    private fun referenceMillis(): Long = settings[GameSettings.XP_REFERENCE_MINUTES] * MILLIS_PER_MINUTE

    private companion object {
        const val XP_STEP = 100
        const val PERCENT = 100.0
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
