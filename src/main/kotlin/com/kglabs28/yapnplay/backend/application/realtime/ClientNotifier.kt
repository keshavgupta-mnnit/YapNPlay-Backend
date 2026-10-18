package com.kglabs28.yapnplay.backend.application.realtime

import com.kglabs28.yapnplay.backend.contract.transport.Envelope

/**
 * Pushes messages to connected users. The WebSocket layer implements it;
 * rooms and (later) matches only know this interface.
 */
interface ClientNotifier {
    /** Queues [envelope] for [userId]'s live connection; does nothing when they are offline. */
    fun send(userId: String, envelope: Envelope)

    /** How many users are connected right now. */
    fun onlineUserCount(): Int

    fun isConnected(userId: String): Boolean
}
