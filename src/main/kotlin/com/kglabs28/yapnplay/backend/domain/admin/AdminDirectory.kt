package com.kglabs28.yapnplay.backend.domain.admin

/** A player as the admin dashboard shows them. */
data class AdminUser(
    val userId: String,
    val displayName: String,
    val userCode: String?,
    val coins: Int,
    val xp: Int,
    val createdAt: Long,
    val playGamesLinked: Boolean,
    /** End of the block running now (epoch millis), or null. */
    val blockedUntil: Long?,
    val friends: Int = 0,
    /** The groups (rooms) they belong to, by name. */
    val groups: List<String> = emptyList(),
    /** Finished matches they played. */
    val gamesPlayed: Int = 0,
    /** Time connected to the app in all (millis), and when they last left (0: not yet). */
    val onlineMs: Long = 0,
    val lastSeenAt: Long = 0,
)

/** A voice-chat report as the admin dashboard shows it. */
data class AdminReport(
    val reportId: String,
    val reporterName: String,
    val reportedId: String,
    val reportedName: String,
    val reason: String,
    val createdAt: Long,
    /** Null while undecided. */
    val upheld: Boolean?,
)

/** A room as the admin dashboard shows it. */
data class AdminRoom(
    val roomId: String,
    val code: String,
    val name: String,
    val description: String?,
    val selectedGame: String?,
    val members: Int,
    val leaderName: String,
    val adminName: String,
    val createdAt: Long,
)

/** Admin-only reads and the one report write the dashboard needs. */
interface AdminDirectory {
    /** By name, user code, Play Games id or id (any part); newest first when [query] is blank. */
    suspend fun searchUsers(query: String, now: Long, limit: Int): List<AdminUser>
    /** By name, code, description, game or id (any part); newest first when [query] is blank. */
    suspend fun searchRooms(query: String, limit: Int): List<AdminRoom>
    /**
     * Deletes a player and everything that points at them (friends, requests,
     * blocks, devices, reports, penalties); finished-match seats keep the
     * match but lose the link. Rooms they created pass to the room's admin.
     * They must already have left every room. False when there was no such player.
     */
    suspend fun deleteUser(userId: String): Boolean
    suspend fun recentReports(limit: Int): List<AdminReport>
    /** Decides a report nobody decided yet; false when already decided or missing. */
    suspend fun decideReport(reportId: String, upheld: Boolean, at: Long): Boolean
    /** Who a report is about, or null. */
    suspend fun reportedIn(reportId: String): String?
}
