package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.voice.VoiceSession
import com.kglabs28.yapnplay.backend.domain.voice.VoiceSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Saves each finished stretch in a voice room for the admin dashboard, off the
 * caller's thread. A blink (shorter than a few seconds: a screen handing over)
 * isn't counted.
 */
class VoiceSessionRecorder(
    private val store: VoiceSessionStore,
    private val scope: CoroutineScope,
) {
    fun ended(session: VoiceSession) {
        if (session.endedAt - session.startedAt < BackendConstants.VOICE_SESSION_MIN_MS) return
        scope.launch {
            runCatching { store.add(session) }.onFailure { log.warn("Voice session not saved (${it.javaClass.simpleName})") }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(VoiceSessionRecorder::class.java)
    }
}
