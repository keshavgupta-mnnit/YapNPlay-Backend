package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.GameRegistry

/** `GET /admin/stats`: database totals plus the live in-memory picture. Read-only. */
class GetAdminStatsUseCase(
    private val queries: AdminStatsQueries,
    private val notifier: ClientNotifier,
    private val matches: MatchRegistry,
    private val presence: RoomPresence,
    private val matchmaking: MatchmakingService,
    private val games: GameRegistry,
    private val clock: Clock,
) {
    suspend operator fun invoke(): AdminStats {
        val now = clock.nowMillis()
        val since = startOfWindow(now)
        val liveMatches = matches.all().filter { !it.isFinished }
        val waiting = matchmaking.waitingNow()
        val roomsActive = presence.activeRoomCount()
        return AdminStats(
            generatedAt = now,
            timeZone = TIME_ZONE,
            live = LiveStats(
                onlineNow = notifier.onlineUserCount(),
                playersInMatchNow = liveMatches.flatMap { it.seatedUsers }.toSet().size,
                liveMatches = liveMatches.size,
                usersInRoomsNow = presence.presentUserCount(),
                roomsActiveNow = roomsActive,
                searchingQuickPlayNow = waiting.values.sum(),
            ),
            users = users(since),
            rooms = RoomStats(queries.roomCount(), roomsActive, queries.roomsPerDay(since, TIME_ZONE)),
            games = gameUsage(
                stored = queries.matchesByGame(),
                live = liveMatches.groupingBy { it.gameId }.eachCount(),
                joins = matchmaking.joinsSinceStart(),
                waiting = waiting,
            ),
            moderation = moderation(now),
        )
    }

    private suspend fun users(since: Long): UserStats {
        val byType = queries.userCountsByType()
        return UserStats(byType.sumOf { it.count }, byType, queries.usersPerDay(since, TIME_ZONE))
    }

    /** Every built-in game (even unplayed ones) plus any id only the history knows, busiest first. */
    private fun gameUsage(
        stored: List<StoredGameCounts>,
        live: Map<String, Int>,
        joins: Map<String, Int>,
        waiting: Map<String, Int>,
    ): List<GameUsage> {
        val storedById = stored.associateBy { it.gameId }
        val ids = (games.games.map { it.id.value } + storedById.keys + live.keys + joins.keys).distinct()
        return ids.map { id ->
            val counts = storedById[id]
            GameUsage(
                gameId = id,
                displayName = games.find(id)?.displayName ?: id,
                matchesFinished = counts?.total ?: 0,
                quickPlayMatches = counts?.quickPlay ?: 0,
                roomMatches = counts?.let { it.total - it.quickPlay } ?: 0,
                humanSeats = counts?.humanSeats ?: 0,
                liveMatches = live[id] ?: 0,
                quickPlayJoinsSinceStart = joins[id] ?: 0,
                searchingNow = waiting[id] ?: 0,
            )
        }.sortedWith(compareByDescending<GameUsage> { it.matchesFinished }.thenByDescending { it.liveMatches })
    }

    private suspend fun moderation(now: Long): ModerationStats {
        val counts = queries.moderationCounts(now)
        return ModerationStats(
            reportsFiled = counts.reports,
            upheld = counts.upheld,
            rejected = counts.rejected,
            pending = counts.pending,
            penaltiesIssued = counts.penalties,
            penaltiesActive = counts.penaltiesActive,
            mostReported = queries.mostReported(MOST_REPORTED_LIMIT),
        )
    }

    /** Midnight in [TIME_ZONE], [DAYS_SHOWN] days ago counting today. */
    private fun startOfWindow(now: Long): Long {
        val zone = java.time.ZoneId.of(TIME_ZONE)
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.minusDays(DAYS_SHOWN - 1L).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    companion object {
        /** The operator is in India; days roll over at IST midnight. */
        const val TIME_ZONE = "Asia/Kolkata"
        const val DAYS_SHOWN = 14
        const val MOST_REPORTED_LIMIT = 10
    }
}
