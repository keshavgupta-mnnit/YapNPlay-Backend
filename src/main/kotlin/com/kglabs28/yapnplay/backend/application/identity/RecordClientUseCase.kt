package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.domain.identity.ClientPlatform
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * After a sign-in: notes where the account was made (the Android app or the
 * web app, and the web device's system), once; later sign-ins elsewhere
 * leave it as it was. Only the admin dashboard reads it.
 */
class RecordClientUseCase(private val users: UserRepository) {
    suspend operator fun invoke(userId: String, userAgent: String?) {
        val client = ClientPlatform.fromUserAgent(userAgent)
        runCatching { users.recordClient(userId, client.platform, client.os) }
    }
}
