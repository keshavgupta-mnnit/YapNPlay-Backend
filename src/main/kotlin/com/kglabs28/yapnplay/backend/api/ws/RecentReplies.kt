package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * The last replies by requestId, so a repeated request (the app retrying
 * after a hiccup) gets the original answer instead of running twice.
 */
class RecentReplies(private val capacity: Int = BackendConstants.WS_RECENT_REPLIES) {
    private val replies = object : LinkedHashMap<String, Envelope>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Envelope>?): Boolean = size > capacity
    }

    fun find(requestId: String): Envelope? = replies[requestId]

    fun remember(requestId: String, reply: Envelope) {
        replies[requestId] = reply
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
