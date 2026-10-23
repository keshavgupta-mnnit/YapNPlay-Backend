package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Time in the app (admin dashboard): from a user's connection opening to it
 * finally closing, added to their account when it closes. A restart loses
 * the sessions open at that moment; one session counts at most [MAX_SESSION_MS].
 */
class OnlineTimeTracker(
    private val users: UserRepository,
    private val clock: Clock,
) {
    private val since = ConcurrentHashMap<String, Long>()

    fun connected(userId: String) {
        since.putIfAbsent(userId, clock.nowMillis())
    }

    suspend fun disconnected(userId: String) {
        val start = since.remove(userId) ?: return
        val now = clock.nowMillis()
        users.addOnlineTime(userId, (now - start).coerceIn(0, MAX_SESSION_MS), now)
    }

    private companion object {
        /** A connection left open for days (a phone on a charger) never counts as days of play. */
        const val MAX_SESSION_MS = 12 * 60 * 60 * 1_000L
    }
}
