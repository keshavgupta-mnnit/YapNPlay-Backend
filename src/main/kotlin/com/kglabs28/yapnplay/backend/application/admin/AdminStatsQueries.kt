package com.kglabs28.yapnplay.backend.application.admin

/** Read-only totals for the admin dashboard, each within a [StatsRange] (every time by default). Never writes anything. */
interface AdminStatsQueries {
    /** Accounts per [AccountType] made in [range], biggest first. */
    suspend fun userCountsByType(range: StatsRange = StatsRange.ALL): List<AccountTypeCount>

    /** New users per calendar day in [zone], from [sinceMillis] until [untilMillis], oldest first. */
    suspend fun usersPerDay(sinceMillis: Long, zone: String, untilMillis: Long = Long.MAX_VALUE): List<DayCount>

    /** Rooms made in [range] (only rooms that still exist). */
    suspend fun roomCount(range: StatsRange = StatsRange.ALL): Int

    /** Rooms created per calendar day in [zone] (only rooms that still exist). */
    suspend fun roomsPerDay(sinceMillis: Long, zone: String, untilMillis: Long = Long.MAX_VALUE): List<DayCount>

    /** Matches that finished in [range], per game, busiest first. */
    suspend fun matchesByGame(range: StatsRange = StatsRange.ALL): List<StoredGameCounts>

    /** Reports filed and penalties given in [range]; the active penalties are as of [nowMillis], whatever the range. */
    suspend fun moderationCounts(nowMillis: Long, range: StatsRange = StatsRange.ALL): StoredModerationCounts

    /** The most reported players, counting reports filed in [range]. */
    suspend fun mostReported(limit: Int, range: StatsRange = StatsRange.ALL): List<ReportedUser>

    /** Matches started in [range] by how they ended, and the play time of the completed ones. */
    suspend fun matchTotals(range: StatsRange = StatsRange.ALL): StoredMatchTotals

    /** Players who left a match before it ended, over matches that ended in [range]. */
    suspend fun playersLeft(range: StatsRange = StatsRange.ALL): Int

    /** Different people (not CPUs) who played a match that ended in [range]. */
    suspend fun playersWhoPlayed(range: StatsRange = StatsRange.ALL): Int

    /** Time in the app over every player (kept only as a running total, so always every time). */
    suspend fun appTime(): StoredAppTime
}
