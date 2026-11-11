package com.kglabs28.yapnplay.backend.domain.match

/** Stores finished matches and the XP each human seat earned. */
interface MatchResultRepository {
    /** Saves [match]; [xpByUser] is added to each user's XP in the same transaction. */
    suspend fun save(match: FinishedMatch, xpByUser: Map<String, Int>)

    /**
     * [limit] matches [userId] played in, newest first, skipping the newest [offset] (a page), with
     * every seat. Each match once, even when they held two seats in it (left, then sat down again).
     */
    suspend fun history(userId: String, limit: Int, offset: Long = 0): List<PlayedMatch>

    /** Every player's record in the room [roomId], by user id (players with no finished match there are absent). */
    suspend fun roomTotals(roomId: String): Map<String, RoomTotals>
}
