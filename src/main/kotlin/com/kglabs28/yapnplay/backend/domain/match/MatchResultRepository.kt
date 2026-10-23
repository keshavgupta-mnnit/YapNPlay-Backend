package com.kglabs28.yapnplay.backend.domain.match

/** Stores finished matches and the XP each human seat earned. */
interface MatchResultRepository {
    /** Saves [match]; [xpByUser] is added to each user's XP in the same transaction. */
    suspend fun save(match: FinishedMatch, xpByUser: Map<String, Int>)

    /** The latest [limit] matches [userId] played in, newest first, with every seat. */
    suspend fun history(userId: String, limit: Int): List<PlayedMatch>
}
