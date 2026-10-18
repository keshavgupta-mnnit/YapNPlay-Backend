package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.config.BackendConstants
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach

/**
 * One user's open WebSocket. Everything sent to it goes through one
 * outgoing queue written by a single coroutine, so messages keep their
 * order. A user too slow to read a full queue is disconnected.
 */
class ClientConnection(
    val userId: String,
    private val session: WebSocketSession,
) {
    private val outgoing = Channel<String>(BackendConstants.WS_OUTGOING_BUFFER)

    fun offer(text: String) {
        if (outgoing.trySend(text).isFailure) outgoing.close()
    }

    /** Runs until the connection ends: writes queued messages in order. */
    suspend fun pumpOutgoing() {
        outgoing.consumeEach { session.send(Frame.Text(it)) }
    }

    suspend fun close(reason: CloseReason) {
        outgoing.close()
        session.close(reason)
    }

    fun stop() {
        outgoing.close()
    }
}
