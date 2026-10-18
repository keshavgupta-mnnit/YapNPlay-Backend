package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.match.actor.MatchCheckpoint

/** Where running matches are saved so a restart can bring them back. */
interface MatchCheckpointStore {
    /** Replaces the saved copy of the match. */
    suspend fun save(checkpoint: MatchCheckpoint, at: Long)

    /** The match ended (or cannot come back): forget it. */
    suspend fun delete(matchId: String)

    /** Every saved match; rows that no longer decode are dropped. */
    suspend fun loadAll(): List<MatchCheckpoint>

    /** Keeps nothing (unit tests that build actors by hand). */
    object None : MatchCheckpointStore {
        override suspend fun save(checkpoint: MatchCheckpoint, at: Long) = Unit
        override suspend fun delete(matchId: String) = Unit
        override suspend fun loadAll(): List<MatchCheckpoint> = emptyList()
    }
}
