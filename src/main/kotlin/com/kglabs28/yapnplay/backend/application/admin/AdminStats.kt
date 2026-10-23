package com.kglabs28.yapnplay.backend.application.admin

import kotlinx.serialization.Serializable

/** `GET /admin/stats`: everything the operator dashboard shows. Not part of the app contract. */
@Serializable
data class AdminStats(
    val generatedAt: Long,
    /** The running build: Gradle version (= the deployed git tag) and short commit. */
    val version: String = "",
    val commit: String = "",
    /** Calendar days below are counted in this time zone. */
    val timeZone: String,
    val live: LiveStats,
    val users: UserStats,
    val rooms: RoomStats,
    val games: List<GameUsage>,
    val moderation: ModerationStats,
    val matches: MatchTotals = MatchTotals(),
)

/** Every match ever started, by how it ended, and the time spent playing. */
@Serializable
data class MatchTotals(
    val completed: Int = 0,
    /** Ended by an error or a server restart before a result. */
    val abandoned: Int = 0,
    /** Minutes of play over all completed matches. */
    val minutesPlayed: Long = 0,
)

/** `GET /admin/uptime` (no token): when this server process started, and its clock now. */
@Serializable
data class ServerUptime(
    val startedAt: Long,
    val serverTime: Long,
)

/** In-memory numbers, reset by a restart. */
@Serializable
data class LiveStats(
    val onlineNow: Int,
    val playersInMatchNow: Int,
    val liveMatches: Int,
    val usersInRoomsNow: Int,
    val roomsActiveNow: Int,
    val searchingQuickPlayNow: Int,
    /** People with their mic on, at a table or in a room lobby. */
    val onMicNow: Int = 0,
    /** People in a room lobby who are not in a match: just chatting. */
    val chattingInLobbyNow: Int = 0,
    val spectatorsNow: Int = 0,
)

/** Player accounts. Today every account is an auto-created guest; no one has signed up. */
@Serializable
data class UserStats(
    val total: Int,
    /** Accounts per sign-in type, biggest first. Only [AccountType.GUEST] exists today. */
    val byType: List<AccountTypeCount>,
    /** New accounts of every type per day. */
    val newPerDay: List<DayCount>,
    /** Time in the app over every player, in minutes, and per player who has used it. */
    val appMinutesTotal: Long = 0,
    val appMinutesAverage: Long = 0,
)

@Serializable
data class AccountTypeCount(
    /** One of [AccountType]. */
    val type: String,
    val count: Int,
)

/** How an account signs in. A new type (Google Play Games) adds a value here and a column in `users`. */
object AccountType {
    const val GUEST = "guest"
}

@Serializable
data class RoomStats(
    /** Rooms that still exist (deleted rooms are gone from the database). */
    val total: Int,
    val activeNow: Int,
    val createdPerDay: List<DayCount>,
)

@Serializable
data class DayCount(
    /** `YYYY-MM-DD` in [AdminStats.timeZone]. */
    val day: String,
    val count: Int,
)

/** One game's popularity: finished matches from the database, the rest live. */
@Serializable
data class GameUsage(
    val gameId: String,
    val displayName: String,
    val matchesFinished: Int,
    val quickPlayMatches: Int,
    val roomMatches: Int,
    /** Human seats over all finished matches. */
    val humanSeats: Int,
    val liveMatches: Int,
    val quickPlayJoinsSinceStart: Int,
    val searchingNow: Int,
)

@Serializable
data class ModerationStats(
    val reportsFiled: Int,
    val upheld: Int,
    val rejected: Int,
    val pending: Int,
    val penaltiesIssued: Int,
    val penaltiesActive: Int,
    val mostReported: List<ReportedUser>,
)

@Serializable
data class ReportedUser(
    val userId: String,
    val displayName: String,
    val userCode: String?,
    val reports: Int,
    val upheld: Int,
)

/** Finished matches of one game, as stored. */
data class StoredGameCounts(
    val gameId: String,
    val total: Int,
    val quickPlay: Int,
    val humanSeats: Int,
)

/** Matches by how they ended, and the play time of completed ones (millis), as stored. */
data class StoredMatchTotals(val completed: Int, val abandoned: Int, val playMillis: Long)

/** Time in the app over every player (millis), and how many players have any. */
data class StoredAppTime(val totalMillis: Long, val players: Int)

/** Report and penalty totals, as stored. */
data class StoredModerationCounts(
    val reports: Int,
    val upheld: Int,
    val rejected: Int,
    val pending: Int,
    val penalties: Int,
    val penaltiesActive: Int,
)
