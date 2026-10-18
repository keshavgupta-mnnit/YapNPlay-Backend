package com.kglabs28.yapnplay.backend.infrastructure.voice

import com.kglabs28.yapnplay.backend.application.voice.VoiceTokenSigner
import com.kglabs28.yapnplay.backend.config.VoiceConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * A LiveKit access token: a JWT (a signed JSON pass) with the API key as
 * issuer, the user as identity, and a grant to join exactly one voice
 * room, signed with the API secret (HS256). LiveKit checks the signature.
 */
class LiveKitTokenSigner(private val config: VoiceConfig) : VoiceTokenSigner {

    override val url: String get() = config.url

    override fun sign(identity: String, displayName: String, voiceRoom: String, issuedAtSeconds: Long, expiresAtSeconds: Long): String {
        val header = buildJsonObject {
            put(ALG, HS256)
            put(TYP, JWT)
        }
        val payload = buildJsonObject {
            put(ISS, config.apiKey)
            put(SUB, identity)
            put(NAME, displayName)
            put(NBF, issuedAtSeconds)
            put(EXP, expiresAtSeconds)
            put(
                VIDEO,
                JsonObject(
                    mapOf(
                        ROOM to JsonPrimitive(voiceRoom),
                        ROOM_JOIN to JsonPrimitive(true),
                        CAN_PUBLISH to JsonPrimitive(true),
                        CAN_SUBSCRIBE to JsonPrimitive(true),
                    ),
                ),
            )
        }
        val unsigned = encode(header) + DOT + encode(payload)
        return unsigned + DOT + base64(hmac(unsigned))
    }

    private fun encode(json: JsonObject): String = base64(Json.encodeToString(JsonObject.serializer(), json).toByteArray(Charsets.UTF_8))

    private fun hmac(data: String): ByteArray = Mac.getInstance(HMAC_SHA256).run {
        init(SecretKeySpec(config.apiSecret.toByteArray(Charsets.UTF_8), HMAC_SHA256))
        doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
        const val DOT = "."
        const val ALG = "alg"
        const val HS256 = "HS256"
        const val TYP = "typ"
        const val JWT = "JWT"
        const val ISS = "iss"
        const val SUB = "sub"
        const val NAME = "name"
        const val NBF = "nbf"
        const val EXP = "exp"
        const val VIDEO = "video"
        const val ROOM = "room"
        const val ROOM_JOIN = "roomJoin"
        const val CAN_PUBLISH = "canPublish"
        const val CAN_SUBSCRIBE = "canSubscribe"
    }
}
