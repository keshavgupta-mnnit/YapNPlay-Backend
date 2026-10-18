package com.kglabs28.yapnplay.backend.application.realtime

import com.kglabs28.yapnplay.backend.contract.Ack
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Rejected
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Builds and reads [Envelope]s with the contract's JSON rules (camelCase
 * names, unknown fields ignored).
 *
 * Reply rule: every request gets exactly one reply with the same
 * requestId: `Ack`, `Rejected`, or a typed result (`JoinRoomResult`,
 * `RoomSnapshot`). Pushes carry a fresh server-made requestId.
 */
class EnvelopeCodec(
    private val json: Json,
    private val ids: IdGenerator,
) {
    fun decode(text: String): Envelope = json.decodeFromString(Envelope.serializer(), text)

    fun encode(envelope: Envelope): String = json.encodeToString(Envelope.serializer(), envelope)

    fun <T> payload(envelope: Envelope, serializer: KSerializer<T>): T =
        json.decodeFromJsonElement(serializer, envelope.payload)

    fun <T> reply(request: Envelope, type: String, serializer: KSerializer<T>, value: T): Envelope =
        build(request.requestId, request.channel, type, serializer, value)

    fun ack(request: Envelope): Envelope =
        reply(request, MessageType.ACK, Ack.serializer(), Ack(request.requestId))

    fun rejected(requestId: String, channel: Channel, code: ErrorCode, message: String): Envelope =
        build(requestId, channel, MessageType.REJECTED, Rejected.serializer(), Rejected(requestId, code, message))

    fun <T> push(channel: Channel, type: String, serializer: KSerializer<T>, value: T): Envelope =
        build(ids.newId(), channel, type, serializer, value)

    private fun <T> build(requestId: String, channel: Channel, type: String, serializer: KSerializer<T>, value: T) =
        Envelope(requestId, channel, type, json.encodeToJsonElement(serializer, value).jsonObject)
}
