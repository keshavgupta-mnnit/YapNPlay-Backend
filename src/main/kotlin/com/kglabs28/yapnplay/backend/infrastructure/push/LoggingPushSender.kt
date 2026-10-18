package com.kglabs28.yapnplay.backend.infrastructure.push

import com.kglabs28.yapnplay.backend.application.push.PushMessage
import com.kglabs28.yapnplay.backend.application.push.PushSender
import org.slf4j.LoggerFactory

/** Used while Firebase is not configured: notes what would have been pushed. */
class LoggingPushSender : PushSender {
    override suspend fun send(userIds: Collection<String>, message: PushMessage) {
        log.info("Push (Firebase not configured) to ${userIds.size} user(s): ${message.data[TYPE_KEY]}")
    }

    private companion object {
        val log = LoggerFactory.getLogger(LoggingPushSender::class.java)
        const val TYPE_KEY = "type"
    }
}
