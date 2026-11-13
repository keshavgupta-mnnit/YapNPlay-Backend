package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings

/**
 * The voice kill switches from the admin dashboard: voice on or off for
 * everyone, and the TURN relay on or off. The relay also stops by itself once
 * the month's relayed data reaches the cap, so a free plan is never overrun.
 */
class VoicePolicy(
    private val settings: Settings,
    private val relayUsage: RelayUsageMeter,
) {
    fun voiceOn(): Boolean = settings[GameSettings.VOICE_ON] == 1

    suspend fun relayAllowed(): Boolean {
        if (settings[GameSettings.VOICE_RELAY_ON] != 1) return false
        val capGb = settings[GameSettings.VOICE_RELAY_CAP_GB]
        return capGb == 0 || relayUsage.thisMonth().bytes < capGb * BackendConstants.BYTES_PER_GB
    }
}
