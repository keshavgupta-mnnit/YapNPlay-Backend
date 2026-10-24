package com.kglabs28.yapnplay.backend.application.social

import com.kglabs28.yapnplay.backend.application.push.PushMessage
import com.kglabs28.yapnplay.backend.application.push.PushSender
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.PushKeys
import org.slf4j.LoggerFactory

/** Phone notifications for friends: a request arrived, and a request was accepted. Never fails the action. */
class FriendPushes(private val push: PushSender) {
    /** Tells [toId] that [fromName] ([fromId]) wants to be friends. */
    suspend fun requested(toId: String, fromId: String, fromName: String) =
        send(toId, PushKeys.TYPE_FRIEND_REQUEST, fromId, BackendStrings.friendRequestTitle(fromName), BackendStrings.FRIEND_REQUEST_BODY)

    /** Tells [toId] (who asked) that [byName] ([byId]) said yes. */
    suspend fun accepted(toId: String, byId: String, byName: String) =
        send(toId, PushKeys.TYPE_FRIEND_ACCEPTED, byId, BackendStrings.friendAcceptedTitle(byName), BackendStrings.FRIEND_ACCEPTED_BODY)

    private suspend fun send(toId: String, type: String, otherId: String, title: String, body: String) {
        val message = PushMessage(mapOf(PushKeys.TYPE to type, PushKeys.USER_ID to otherId, PushKeys.TITLE to title, PushKeys.BODY to body))
        runCatching { push.send(listOf(toId), message) }.onFailure { log.warn("Friend push failed", it) }
    }

    private companion object {
        val log = LoggerFactory.getLogger(FriendPushes::class.java)
    }
}
