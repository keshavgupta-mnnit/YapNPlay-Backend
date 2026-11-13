package com.kglabs28.yapnplay.backend.application.voice

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Who a voice pass lets in, where, and until when (epoch ms). */
data class VoicePass(val userId: String, val voiceRoom: String, val expiresAt: Long)

/**
 * Voice passes: "<payload>.<signature>", the payload naming the user, the voice
 * room and the expiry, signed (HMAC-SHA256) with a key made when the server
 * starts. A restart makes old passes useless; the app then asks for a new one.
 */
class VoicePasses(private val key: ByteArray = newKey()) {

    fun sign(pass: VoicePass): String {
        val payload = listOf(pass.userId, pass.voiceRoom, pass.expiresAt.toString()).joinToString(FIELD)
        val encoded = base64(payload.toByteArray(Charsets.UTF_8))
        return encoded + DOT + base64(hmac(encoded))
    }

    /** The pass [token] carries, or null when it is forged, broken or expired at [nowMillis]. */
    fun verify(token: String, nowMillis: Long): VoicePass? {
        val parts = token.split(DOT)
        if (parts.size != 2) return null
        val given = runCatching { decoder.decode(parts[1]) }.getOrNull() ?: return null
        if (!MessageDigest.isEqual(hmac(parts[0]), given)) return null
        val fields = runCatching { String(decoder.decode(parts[0]), Charsets.UTF_8) }.getOrNull()?.split(FIELD) ?: return null
        if (fields.size != FIELD_COUNT) return null
        val expiresAt = fields[2].toLongOrNull() ?: return null
        return VoicePass(fields[0], fields[1], expiresAt).takeIf { expiresAt > nowMillis }
    }

    private fun hmac(data: String): ByteArray = Mac.getInstance(HMAC_SHA256).run {
        init(SecretKeySpec(key, HMAC_SHA256))
        doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
        const val DOT = "."
        const val FIELD = "\n"
        const val FIELD_COUNT = 3
        const val KEY_BYTES = 32
        val decoder: Base64.Decoder = Base64.getUrlDecoder()

        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
    }
}
