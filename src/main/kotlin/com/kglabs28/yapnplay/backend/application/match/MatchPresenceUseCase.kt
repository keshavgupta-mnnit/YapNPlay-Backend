package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction

/**
 * Tells matches about the user's connection and room presence: a dropped
 * connection starts the away grace for their seat and ends watching;
 * leaving the room ends watching that room's match.
 */
class MatchPresenceUseCase(private val registry: MatchRegistry) {

    suspend fun disconnected(userId: String) {
        registry.all().filter { !it.isFinished && userId in it.presentUsers + it.seatedUsers }
            .forEach { it.ask(userId, MatchAction.Disconnected) }
    }

    suspend fun leftRoom(userId: String, roomId: String) {
        registry.all().filter { !it.isFinished && it.roomId == roomId && userId in it.presentUsers && userId !in it.seatedUsers }
            .forEach { it.ask(userId, MatchAction.StopWatching) }
    }
}
