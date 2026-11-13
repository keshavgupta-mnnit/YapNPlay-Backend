package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.VoiceToken
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearbyVoiceTokenApiTest {

    @Test
    fun `only a Nearby party id gets a Nearby voice pass`() = serverTest { client ->
        val token = client.newGuestToken()
        for (bad in listOf("room-abc", "nearby-room-AB", "nearby-other-abcdefgh", "nearby-room-abc")) {
            val body = client.post("/nearby/" + bad + "/voice-token") { bearerAuth(token) }.body<ErrorBody>()
            assertEquals(ErrorCode.BAD_REQUEST, body.code, bad)
        }
    }

    @Test
    fun `a Nearby party id gets a pass with STUN servers`() = serverTest { client ->
        val token = client.newGuestToken()
        val pass = client.post("/nearby/nearby-room-abc123def456/voice-token") { bearerAuth(token) }.body<VoiceToken>()
        assertEquals("/ws", pass.url)
        assertTrue(pass.token.isNotBlank())
        assertTrue(pass.iceServers.any { server -> server.urls.any { it.startsWith("stun:") } })
    }

    @Test
    fun `a Nearby voice pass needs sign-in`() = serverTest { client ->
        val body = client.post("/nearby/nearby-room-abc123def456/voice-token").body<ErrorBody>()
        assertEquals(ErrorCode.NOT_AUTHENTICATED, body.code)
    }
}
