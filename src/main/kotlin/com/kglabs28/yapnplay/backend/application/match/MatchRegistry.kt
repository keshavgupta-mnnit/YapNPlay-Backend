package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.match.actor.MatchActor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** Live matches by id, and which match each seated user is playing. */
class MatchRegistry {
    private val matches = ConcurrentHashMap<String, MatchActor>()

    fun add(actor: MatchActor) {
        matches[actor.matchId] = actor
    }

    fun find(matchId: String): MatchActor? = matches[matchId]

    fun remove(matchId: String) {
        matches.remove(matchId)
    }

    /** The unfinished match [userId] holds a seat in, if any. */
    fun activeMatchOf(userId: String): MatchActor? =
        matches.values.firstOrNull { !it.isFinished && userId in it.seatedUsers }

    /** Every unfinished match that might know [userId] (as player or spectator). */
    fun all(): Collection<MatchActor> = matches.values

    /** Stops every match, giving their last checkpoints a moment to reach the database. */
    fun shutdown() {
        val stopping = matches.values.toList()
        stopping.forEach { it.stop() }
        matches.clear()
        runBlocking { withTimeoutOrNull(SAVE_ON_SHUTDOWN_MS) { stopping.forEach { it.awaitSaved() } } }
    }

    private companion object {
        const val SAVE_ON_SHUTDOWN_MS = 3_000L
    }
}
