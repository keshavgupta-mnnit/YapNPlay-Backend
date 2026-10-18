package com.kglabs28.yapnplay.backend.application.voice

/** Creates a voice-provider access token for one person in one voice room. */
interface VoiceTokenSigner {
    /** The provider's WebSocket URL the app connects to. */
    val url: String

    fun sign(identity: String, displayName: String, voiceRoom: String, issuedAtSeconds: Long, expiresAtSeconds: Long): String
}
