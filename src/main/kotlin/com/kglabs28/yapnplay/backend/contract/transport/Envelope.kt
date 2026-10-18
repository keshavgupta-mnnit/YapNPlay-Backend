package com.kglabs28.yapnplay.backend.contract.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The wire envelope sent over the WebSocket.
 *
 * - [requestId]: UUID text; the server echoes it in Ack/Rejected so the client
 *   can match responses to requests. Duplicates with the same ID are ignored.
 * - [channel]: which logical channel (ROOM or MATCH).
 * - [type]: the message type string (see [MessageType]).
 * - [payload]: the JSON payload; deserialize based on [type].
 */
@Serializable
data class Envelope(
    val requestId: String,
    val channel: Channel,
    val type: String,
    val payload: JsonObject,
)
