package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.config.VoiceConfig
import com.kglabs28.yapnplay.backend.infrastructure.voice.LiveKitTokenSigner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveKitTokenSignerTest {
    private val config = VoiceConfig("wss://example.livekit.cloud", "APIkey", "a-test-secret-that-is-long-enough")
    private val signer = LiveKitTokenSigner(config)

    @Test
    fun `the token is a JWT for one user in one voice room, signed with the secret`() {
        val token = signer.sign("user-1", "Keshav", "room-abc", 1_000, 1_600)
        val (header, payload, signature) = token.split(".")
        val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload))).jsonObject
        assertEquals("APIkey", claims.getValue("iss").jsonPrimitive.content)
        assertEquals("user-1", claims.getValue("sub").jsonPrimitive.content)
        assertEquals("1600", claims.getValue("exp").jsonPrimitive.content)
        assertEquals("room-abc", claims.getValue("video").jsonObject.getValue("room").jsonPrimitive.content)
        val expected = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(config.apiSecret.toByteArray(), "HmacSHA256"))
            Base64.getUrlEncoder().withoutPadding().encodeToString(doFinal("$header.$payload".toByteArray()))
        }
        assertEquals(expected, signature)
    }
}
