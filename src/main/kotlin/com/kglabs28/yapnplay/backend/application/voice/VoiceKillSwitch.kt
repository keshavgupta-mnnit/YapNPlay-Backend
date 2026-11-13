package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.config.BackendConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Applies the voice kill switches to people already talking: every few seconds,
 * when voice is switched off, or the relay is switched off or reaches its cap,
 * every voice room is closed. Phones then join again with a fresh pass (no relay)
 * or, with voice off, stay out until it is back on.
 */
class VoiceKillSwitch(
    private val policy: VoicePolicy,
    private val hub: VoiceHub,
    scope: CoroutineScope,
) {
    private var voiceWasOn = true
    private var relayWasAllowed = true

    init {
        scope.launch {
            while (true) {
                delay(BackendConstants.VOICE_SWITCH_CHECK_MS)
                runCatching { check() }.onFailure { log.warn("Voice switch check failed (${it.javaClass.simpleName})") }
            }
        }
    }

    suspend fun check() {
        val voiceOn = policy.voiceOn()
        val relayAllowed = policy.relayAllowed()
        if ((voiceWasOn && !voiceOn) || (relayWasAllowed && !relayAllowed)) {
            log.info("Voice rooms closed: voice ${if (voiceOn) "on" else "off"}, relay ${if (relayAllowed) "allowed" else "stopped"}")
            hub.closeAll()
        }
        voiceWasOn = voiceOn
        relayWasAllowed = relayAllowed
    }

    private companion object {
        val log = LoggerFactory.getLogger(VoiceKillSwitch::class.java)
    }
}
