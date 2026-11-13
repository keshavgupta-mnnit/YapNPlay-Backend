package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.config.BuildInfo
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import com.kglabs28.yapnplay.backend.application.voice.RelayUsageMeter
import com.kglabs28.yapnplay.backend.application.voice.VoicePolicy
import com.kglabs28.yapnplay.backend.domain.voice.VoiceSessionStore
import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.config.BackendConstants

/** `GET /admin/stats`: database totals plus the live in-memory picture. Read-only. */
class GetAdminStatsUseCase(
    private val queries: AdminStatsQueries,
    private val notifier: ClientNotifier,
    private val matches: MatchRegistry,
    private val presence: RoomPresence,
    private val matchmaking: MatchmakingService,
    private val games: GameRegistry,
    private val clock: Clock,
    private val rooms: PlayRoomActors,
    private val relayUsage: RelayUsageMeter,
    private val voicePolicy: VoicePolicy,
    private val settings: Settings,
    private val voiceSessions: VoiceSessionStore,
    private val relayStore: RelayUsageStore,
) {
    /** Totals within [range]; the per-day charts cover the range, or the last [DAYS_SHOWN] days for every time. */
    suspend operator fun invoke(range: StatsRange = StatsRange.ALL): AdminStats {
        val now = clock.nowMillis()
        val since = if (range.isAll) startOfWindow(now) else range.fromMillis
        val until = if (range.isAll) Long.MAX_VALUE else range.toMillis
        val liveMatches = matches.all().filter { !it.isFinished }
        val waiting = matchmaking.waitingNow()
        val roomsActive = presence.activeRoomCount()
        return AdminStats(
            generatedAt = now,
            version = BuildInfo.version,
            commit = BuildInfo.commit,
            timeZone = TIME_ZONE,
            rangeFrom = range.fromMillis.takeUnless { range.isAll },
            rangeTo = range.toMillis.takeUnless { range.isAll || it == Long.MAX_VALUE },
            live = LiveStats(
                onlineNow = notifier.onlineUserCount(),
                playersInMatchNow = liveMatches.flatMap { it.seatedUsers }.toSet().size,
                liveMatches = liveMatches.size,
                usersInRoomsNow = presence.presentUserCount(),
                roomsActiveNow = roomsActive,
                searchingQuickPlayNow = waiting.values.sum(),
                onMicNow = (liveMatches.flatMap { it.micOnUsers } + rooms.all().flatMap { it.micOnUsers }).toSet().size,
                chattingInLobbyNow = (presence.presentUsers() - liveMatches.flatMap { it.seatedUsers }.toSet()).size,
                spectatorsNow = liveMatches.sumOf { it.spectatorCount },
                playersWhoPlayed = queries.playersWhoPlayed(range),
            ),
            users = users(range, since, until),
            rooms = RoomStats(queries.roomCount(range), roomsActive, queries.roomsPerDay(since, TIME_ZONE, until)),
            games = gameUsage(
                stored = queries.matchesByGame(range),
                live = liveMatches.groupingBy { it.gameId }.eachCount(),
                joins = matchmaking.joinsSinceStart(),
                waiting = waiting,
            ),
            moderation = moderation(now, range),
            matches = queries.matchTotals(range).let { MatchTotals(it.completed, it.abandoned, it.playMillis / MILLIS_PER_MINUTE, queries.playersLeft(range)) },
            voiceRelay = relayUsage.thisMonth().let {
                VoiceRelayStats(
                    month = it.month,
                    bytes = it.bytes,
                    warnBytes = settings[GameSettings.VOICE_RELAY_WARN_GB] * BackendConstants.BYTES_PER_GB,
                    limitBytes = settings[GameSettings.VOICE_RELAY_PLAN_GB] * BackendConstants.BYTES_PER_GB,
                    capBytes = settings[GameSettings.VOICE_RELAY_CAP_GB] * BackendConstants.BYTES_PER_GB,
                    voiceOn = voicePolicy.voiceOn(),
                    relayActive = voicePolicy.relayAllowed(),
                )
            },
            voiceUsage = voiceUsage(since, until),
        )
    }

    private suspend fun users(range: StatsRange, since: Long, until: Long): UserStats {
        val byType = queries.userCountsByType(range)
        val time = queries.appTime()
        return UserStats(
            byType.sumOf { it.count }, byType, queries.usersPerDay(since, TIME_ZONE, until),
            appMinutesTotal = time.totalMillis / MILLIS_PER_MINUTE,
            appMinutesAverage = if (time.players == 0) 0 else time.totalMillis / time.players / MILLIS_PER_MINUTE,
        )
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

    private suspend fun moderation(now: Long, range: StatsRange): ModerationStats {
        val counts = queries.moderationCounts(now, range)
        return ModerationStats(
            reportsFiled = counts.reports,
            upheld = counts.upheld,
            rejected = counts.rejected,
            pending = counts.pending,
            penaltiesIssued = counts.penalties,
            penaltiesActive = counts.penaltiesActive,
            mostReported = queries.mostReported(MOST_REPORTED_LIMIT, range),
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
        const val TIME_ZONE = BackendConstants.STATS_TIME_ZONE
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of(TIME_ZONE))
        const val DAYS_SHOWN = 14
        const val MOST_REPORTED_LIMIT = 10
        const val SECONDS_PER_MINUTE = 60L
    }

    /** Voice use for the period: people, sessions, minutes and relayed data, and the same per day. */
    private suspend fun voiceUsage(since: Long, until: Long): VoiceUsageStats {
        val totals = voiceSessions.totals(since, until)
        val minutes = totals.seconds / SECONDS_PER_MINUTE
        val fromDay = dayOf(since)
        val toDay = dayOf(minOf(until - 1, clock.nowMillis()))
        val relayDays = relayStore.daily(fromDay, toDay).associateBy { it.day }
        val relayUsers = relayStore.users(fromDay, toDay)
        val relayBytes = relayDays.values.sumOf { it.bytes }
        val sessionDays = voiceSessions.perDay(since, until, TIME_ZONE).associateBy { it.day }
        return VoiceUsageStats(
            users = totals.users,
            sessions = totals.sessions,
            minutes = minutes,
            averageMinutesPerUser = if (totals.users == 0) 0 else minutes / totals.users,
            relayUsers = relayUsers,
            relayBytes = relayBytes,
            averageRelayBytesPerUser = if (relayUsers == 0) 0 else relayBytes / relayUsers,
            perDay = (sessionDays.keys + relayDays.keys).sorted().map { day ->
                val s = sessionDays[day]
                val r = relayDays[day]
                VoiceDayStats(day, s?.users ?: 0, s?.sessions ?: 0, (s?.seconds ?: 0) / SECONDS_PER_MINUTE, r?.users ?: 0, r?.bytes ?: 0)
            },
        )
    }

    private fun dayOf(millis: Long): String = DAY.format(Instant.ofEpochMilli(millis.coerceAtLeast(0)))

}

/** A minute in millis (the dashboard shows minutes). */
private const val MILLIS_PER_MINUTE = 60_000L
