package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository

/**
 * When a match ends: store the result, give every human seat its XP
 * (Quick Play and room matches alike), and free the room's lobby.
 */
class MatchResultRecorder(
    private val results: MatchResultRepository,
    private val progression: ProgressionPolicy,
    private val rooms: RoomGateway,
) : MatchLifecycleListener {

    override suspend fun onFinished(match: FinishedMatch) {
        // Decided when the match ended (place and match length), and shown on the result screen.
        val xp = match.seats.mapNotNull { seat -> seat.userId?.let { it to seat.xp } }.toMap()
        results.save(match, xp)
        match.roomId?.let { rooms.matchEnded(it, match.matchId) }
    }
}
