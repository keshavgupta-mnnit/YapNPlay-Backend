package com.kglabs28.yapnplay.backend.application.match

/** How a match ended, kept so unfinished games can be counted. */
enum class MatchOutcomeStatus {
    RUNNING,
    /** Played to the end. */
    COMPLETED,
    /** The server restarted or crashed and the match could not come back. */
    ABORTED_SERVER,
    /** The match broke on an error it could not get past. */
    ABORTED_ERROR,
}

/** One row per match from its start: the outcome record (`match_outcomes`). */
interface MatchOutcomeStore {
    suspend fun started(matchId: String, gameId: String, roomId: String?, startedAt: Long)

    /** Closes a RUNNING match; a match already closed keeps its first outcome. */
    suspend fun ended(matchId: String, status: MatchOutcomeStatus, endedAt: Long, reason: String? = null)

    /** One action failed unexpectedly (the match went on). */
    suspend fun errorSeen(matchId: String)

    /** Matches still marked RUNNING. */
    suspend fun running(): List<String>

    /** Records nothing (unit tests that build actors by hand). */
    object None : MatchOutcomeStore {
        override suspend fun started(matchId: String, gameId: String, roomId: String?, startedAt: Long) = Unit
        override suspend fun ended(matchId: String, status: MatchOutcomeStatus, endedAt: Long, reason: String?) = Unit
        override suspend fun errorSeen(matchId: String) = Unit
        override suspend fun running(): List<String> = emptyList()
    }
}
