package com.kglabs28.yapnplay.backend.application.admin

/** Read-only totals for the admin dashboard. Never writes anything. */
interface AdminStatsQueries {
    /** Accounts per [AccountType], biggest first. */
    suspend fun userCountsByType(): List<AccountTypeCount>

    /** New users per calendar day in [zone], from [sinceMillis] on, oldest first. */
    suspend fun usersPerDay(sinceMillis: Long, zone: String): List<DayCount>

    suspend fun roomCount(): Int

    /** Rooms created per calendar day in [zone] (only rooms that still exist). */
    suspend fun roomsPerDay(sinceMillis: Long, zone: String): List<DayCount>

    /** Finished matches per game, busiest first. */
    suspend fun matchesByGame(): List<StoredGameCounts>

    suspend fun moderationCounts(nowMillis: Long): StoredModerationCounts

    suspend fun mostReported(limit: Int): List<ReportedUser>
}
