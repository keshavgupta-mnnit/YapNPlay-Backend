package com.kglabs28.yapnplay.backend.config

/** LiveKit connection settings, from the environment only (the secret never goes in code or git). */
data class VoiceConfig(
    val url: String,
    val apiKey: String,
    val apiSecret: String,
) {
    override fun toString(): String = "VoiceConfig(url=$url, apiKey=$apiKey, apiSecret=***)"
}
