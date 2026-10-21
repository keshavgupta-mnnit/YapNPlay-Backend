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

/** Admin-only reads and the one report write the dashboard needs. */
interface AdminDirectory {
    /** By name, user code or id; newest first when [query] is blank. */
    suspend fun searchUsers(query: String, now: Long, limit: Int): List<AdminUser>
    suspend fun recentReports(limit: Int): List<AdminReport>
    /** Decides a report nobody decided yet; false when already decided or missing. */
    suspend fun decideReport(reportId: String, upheld: Boolean, at: Long): Boolean
    /** Who a report is about, or null. */
    suspend fun reportedIn(reportId: String): String?
}
