package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import java.util.concurrent.ConcurrentHashMap

/** One live connection per user; a new one replaces the old. */
class ConnectionRegistry(private val codec: EnvelopeCodec) : ClientNotifier {
    private val connections = ConcurrentHashMap<String, ClientConnection>()

    /** Registers [connection] and returns the one it replaced, if any. */
    fun register(connection: ClientConnection): ClientConnection? = connections.put(connection.userId, connection)

    /** Removes [connection]; false when a newer connection had already replaced it. */
    fun unregister(connection: ClientConnection): Boolean = connections.remove(connection.userId, connection)

    override fun send(userId: String, envelope: Envelope) {
        connections[userId]?.offer(codec.encode(envelope))
    }

    override fun onlineUserCount(): Int = connections.size

    override fun isConnected(userId: String): Boolean = connections.containsKey(userId)
}
