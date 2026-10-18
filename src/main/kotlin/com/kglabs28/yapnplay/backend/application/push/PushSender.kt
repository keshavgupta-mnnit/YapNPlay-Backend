package com.kglabs28.yapnplay.backend.application.push

/**
 * Delivers pushes to phones. Production: Firebase Cloud Messaging; when
 * Firebase is not configured, a sender that only logs.
 */
fun interface PushSender {
    /** Sends [message] to every device of [userIds]; failures are logged, never thrown. */
    suspend fun send(userIds: Collection<String>, message: PushMessage)
}
