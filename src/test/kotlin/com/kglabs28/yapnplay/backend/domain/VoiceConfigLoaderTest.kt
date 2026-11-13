package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.config.BackendConfigLoader
import com.kglabs28.yapnplay.backend.config.TurnServer
import com.kglabs28.yapnplay.backend.contract.IceServer
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.infrastructure.voice.ConfiguredIceServers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VoiceConfigLoaderTest {

    private fun load(vararg pairs: Pair<String, String>) =
        BackendConfigLoader(mapOf("DEV_MODE" to "true", *pairs)::get).load().voice

    @Test
    fun `two fixed TURN servers are read and handed out after STUN`() = runTest {
        val voice = load(
            "TURN_URLS" to "turn:a.example.com:3478, turn:a.example.com:3478?transport=tcp",
            "TURN_USERNAME" to "ua",
            "TURN_CREDENTIAL" to "pa",
            "TURN2_URLS" to "turns:b.example.com:443?transport=tcp",
            "TURN2_USERNAME" to "ub",
            "TURN2_CREDENTIAL" to "pb",
        )
        assertEquals(
            listOf(
                TurnServer(listOf("turn:a.example.com:3478", "turn:a.example.com:3478?transport=tcp"), "ua", "pa"),
                TurnServer(listOf("turns:b.example.com:443?transport=tcp"), "ub", "pb"),
            ),
            voice.fixedTurn,
        )
        val servers = ConfiguredIceServers(voice, Clock { 0L }).current(withRelay = true)
        assertEquals(3, servers.size)
        assertEquals(1, ConfiguredIceServers(voice, Clock { 0L }).current(withRelay = false).size)
        assertEquals(IceServer(listOf("turns:b.example.com:443?transport=tcp"), "ub", "pb"), servers[2])
    }

    @Test
    fun `no TURN settings means STUN only and secrets never print`() = runTest {
        val voice = load("TURN2_CREDENTIAL" to "secret-pass")
        assertEquals(emptyList(), voice.fixedTurn)
        assertEquals(1, ConfiguredIceServers(voice, Clock { 0L }).current(withRelay = true).size)
        assertFalse(load("TURN_URLS" to "turn:x:3478", "TURN_CREDENTIAL" to "secret-pass").toString().contains("secret-pass"))
    }
}
