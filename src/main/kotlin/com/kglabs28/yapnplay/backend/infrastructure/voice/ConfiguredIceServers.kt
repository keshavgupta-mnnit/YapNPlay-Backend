package com.kglabs28.yapnplay.backend.infrastructure.voice

import com.kglabs28.yapnplay.backend.application.voice.IceServerSource
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.VoiceConfig
import com.kglabs28.yapnplay.backend.contract.IceServer
import com.kglabs28.yapnplay.backend.domain.common.Clock

/**
 * Free public STUN, plus every TURN relay that is set up: fixed ones from the
 * environment, and Cloudflare's (credentials made on demand, kept for a day).
 * Phones try them all; a relay that can't be reached leaves the others.
 */
class ConfiguredIceServers(
    private val config: VoiceConfig,
    private val clock: Clock,
    private val cloudflare: CloudflareTurnClient? = config.takeIf { it.hasCloudflare }?.let { CloudflareTurnClient(it.cloudflareKeyId!!, it.cloudflareApiToken!!) },
) : IceServerSource {

    private val stun = IceServer(BackendConstants.STUN_URLS)
    private val fixedTurn = config.fixedTurn.map { IceServer(it.urls, it.username, it.credential) }

    @Volatile private var cached: List<IceServer> = emptyList()
    @Volatile private var cachedUntil = 0L
    @Volatile private var retryAt = 0L

    override suspend fun current(withRelay: Boolean): List<IceServer> =
        if (withRelay) listOf(stun) + fixedTurn + cloudflareServers() else listOf(stun)

    private suspend fun cloudflareServers(): List<IceServer> {
        val client = cloudflare ?: return emptyList()
        val now = clock.nowMillis()
        if (now < cachedUntil - BackendConstants.TURN_REFRESH_MARGIN_SECONDS * MILLIS || now < retryAt) return cached
        val fresh = client.iceServers(BackendConstants.TURN_CREDENTIAL_TTL_SECONDS)
        if (fresh == null) {
            retryAt = now + BackendConstants.TURN_RETRY_SECONDS * MILLIS
        } else {
            cached = fresh
            cachedUntil = now + BackendConstants.TURN_CREDENTIAL_TTL_SECONDS * MILLIS
        }
        return cached.takeIf { now < cachedUntil }.orEmpty()
    }

    private companion object {
        const val MILLIS = 1_000L
    }
}
