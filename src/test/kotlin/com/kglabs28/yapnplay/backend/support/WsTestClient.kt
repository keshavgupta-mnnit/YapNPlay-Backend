package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/** Sends envelopes and waits for specific replies or pushes in a WebSocket test. */
class WsTestClient(private val session: DefaultClientWebSocketSession) {
    private val seen = ArrayDeque<Envelope>()

    suspend fun <T> send(type: String, serializer: KSerializer<T>, payload: T, requestId: String = newId()): String {
        sendRaw(testJson.encodeToString(Envelope.serializer(), envelope(requestId, type, serializer, payload)))
        return requestId
    }

    /** Like [send] but on another [channel] (MATCH, MATCHMAKING, MODERATION). */
    suspend fun <T> sendOn(channel: Channel, type: String, serializer: KSerializer<T>, payload: T, requestId: String = newId()): String {
        val envelope = Envelope(requestId, channel, type, testJson.encodeToJsonElement(serializer, payload).jsonObject)
        sendRaw(testJson.encodeToString(Envelope.serializer(), envelope))
        return requestId
    }

    suspend fun sendRaw(text: String) {
        session.send(Frame.Text(text))
    }

    /** The reply to [requestId] (Ack, Rejected or a typed result). */
    suspend fun reply(requestId: String): Envelope = next { it.requestId == requestId }

    /** The next message of [type], skipping others. */
    suspend fun next(type: String, timeoutMs: Long = TIMEOUT_MS): Envelope = next(timeoutMs) { it.type == type }

    suspend fun next(timeoutMs: Long = TIMEOUT_MS, match: (Envelope) -> Boolean): Envelope = withTimeout(timeoutMs) {
        seen.firstOrNull(match)?.let { seen.remove(it); return@withTimeout it }
        while (true) {
            val frame = session.incoming.receive()
            if (frame !is Frame.Text) continue
            val envelope = testJson.decodeFromString(Envelope.serializer(), frame.readText())
            if (match(envelope)) return@withTimeout envelope
            seen.addLast(envelope)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    fun <T> decode(envelope: Envelope, serializer: KSerializer<T>): T = testJson.decodeFromJsonElement(serializer, envelope.payload)

    private fun <T> envelope(requestId: String, type: String, serializer: KSerializer<T>, payload: T) =
        Envelope(requestId, Channel.ROOM, type, testJson.encodeToJsonElement(serializer, payload).jsonObject)

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
