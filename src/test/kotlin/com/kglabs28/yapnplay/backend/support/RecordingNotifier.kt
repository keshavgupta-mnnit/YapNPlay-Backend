package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier

/** Remembers every push instead of sending it. */
class RecordingNotifier : ClientNotifier {
    val sent = mutableListOf<Pair<String, Envelope>>()

    override fun send(userId: String, envelope: Envelope) {
        sent += userId to envelope
    }

    override fun onlineUserCount(): Int = sent.map { it.first }.distinct().size

    override fun isConnected(userId: String): Boolean = true

    /** App features (beyond the first games) every user's app reports; none: an older app. */
    var features: Set<String> = emptySet()

    override fun supports(userId: String, gameId: String): Boolean = gameId in features || super.supports(userId, gameId)

    fun typesFor(userId: String): List<String> = sent.filter { it.first == userId }.map { it.second.type }
}
