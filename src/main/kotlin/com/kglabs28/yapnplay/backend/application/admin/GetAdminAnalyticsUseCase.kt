package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * `GET /admin/analytics`: one period's players, matches, sources and games,
 * with a timeline (per hour up to two days, else per day; empty buckets
 * included so charts line up). "All time" runs from the first account.
 */
class GetAdminAnalyticsUseCase(
    private val analytics: AdminAnalyticsQueries,
    private val stats: AdminStatsQueries,
    private val games: GameRegistry,
    private val clock: Clock,
) {
    suspend operator fun invoke(range: StatsRange = StatsRange.ALL): AdminAnalytics {
        val now = clock.nowMillis()
        val zone = ZoneId.of(GetAdminStatsUseCase.TIME_ZONE)
        val from = if (range.isAll) analytics.firstAccountAt() ?: startOfDay(now, zone) else range.fromMillis
        val to = minOf(range.toMillis, now)
        val period = StatsRange(from, to)
        val hourly = to - from <= HOURLY_UP_TO_MS
        val timeline = timeline(period, zone, hourly)
        val outcomes = analytics.gameOutcomes(period)
        val players = analytics.gamePlayers(period)
        val totals = stats.matchTotals(period)
        val started = outcomes.sumOf { it.started }
        return AdminAnalytics(
            generatedAt = now,
            timeZone = zone.id,
            rangeFrom = from,
            rangeTo = to,
            allTime = range.isAll,
            bucket = if (hourly) AdminAnalytics.BUCKET_HOUR else AdminAnalytics.BUCKET_DAY,
            summary = AnalyticsSummary(
                newPlayers = timeline.sumOf { it.newPlayers },
                activePlayers = stats.playersWhoPlayed(period),
                matchesStarted = started,
                matchesCompleted = totals.completed,
                matchesStopped = totals.abandoned,
                matchesRunning = (started - totals.completed - totals.abandoned).coerceAtLeast(0),
                humanSeats = players.sumOf { it.humanSeats },
                playersLeft = stats.playersLeft(period),
                minutesPlayed = totals.playMillis / MILLIS_PER_MINUTE,
            ),
            timeline = timeline,
            sources = analytics.sources(period),
            allTimeSources = analytics.sources(StatsRange.ALL),
            games = gameRows(outcomes, players),
        )
    }

    private suspend fun timeline(period: StatsRange, zone: ZoneId, hourly: Boolean): List<AnalyticsPoint> {
        val format = if (hourly) SQL_HOUR else SQL_DAY
        val newPlayers = analytics.newPlayers(period, zone.id, format).associate { it.label to it.count }
        val active = analytics.activePlayers(period, zone.id, format).associate { it.label to it.count }
        val matches = analytics.matches(period, zone.id, format).associateBy { it.label }
        return labels(period, zone, hourly).map { label ->
            val m = matches[label]
            AnalyticsPoint(label, newPlayers[label] ?: 0, active[label] ?: 0, m?.started ?: 0, m?.completed ?: 0, m?.stopped ?: 0)
        }
    }

    /** Every hour or day from the period's start to its end, as the SQL labels them. */
    private fun labels(period: StatsRange, zone: ZoneId, hourly: Boolean): List<String> {
        val unit = if (hourly) ChronoUnit.HOURS else ChronoUnit.DAYS
        val pattern = DateTimeFormatter.ofPattern(if (hourly) KOTLIN_HOUR else KOTLIN_DAY)
        var at: ZonedDateTime = Instant.ofEpochMilli(period.fromMillis).atZone(zone).truncatedTo(unit)
        val end = Instant.ofEpochMilli(period.toMillis).atZone(zone)
        val out = mutableListOf<String>()
        while (at.isBefore(end) && out.size < MAX_POINTS) {
            out += at.format(pattern)
            at = at.plus(1, unit)
        }
        return out
    }

    /** Every built-in game (even unplayed) plus any id only the history knows; biggest audience first. */
    private fun gameRows(outcomes: List<StoredGameOutcomes>, players: List<StoredGamePlayers>): List<GameAnalytics> {
        val byOutcome = outcomes.associateBy { it.gameId }
        val byPlayers = players.associateBy { it.gameId }
        val ids = (games.games.map { it.id.value } + byOutcome.keys + byPlayers.keys).distinct()
        return ids.map { id ->
            val o = byOutcome[id]
            val p = byPlayers[id]
            GameAnalytics(
                gameId = id,
                displayName = games.find(id)?.displayName ?: id,
                matchesStarted = o?.started ?: 0,
                matchesCompleted = o?.completed ?: 0,
                matchesStopped = o?.stopped ?: 0,
                players = p?.players ?: 0,
                humanSeats = p?.humanSeats ?: 0,
                playersLeft = p?.playersLeft ?: 0,
                quickPlayMatches = p?.quickPlayMatches ?: 0,
                roomMatches = p?.let { it.finishedMatches - it.quickPlayMatches } ?: 0,
            )
        }.sortedWith(compareByDescending<GameAnalytics> { it.players }.thenByDescending { it.matchesStarted }.thenBy { it.displayName })
    }

    private fun startOfDay(now: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(now).atZone(zone).truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli()

    private companion object {
        /** Periods up to two days are shown per hour. */
        const val HOURLY_UP_TO_MS = 48 * 60 * 60 * 1000L
        /** A timeline never has more points than this (about three years of days). */
        const val MAX_POINTS = 1_100
        const val SQL_HOUR = "YYYY-MM-DD HH24:00"
        const val SQL_DAY = "YYYY-MM-DD"
        const val KOTLIN_HOUR = "yyyy-MM-dd HH:00"
        const val KOTLIN_DAY = "yyyy-MM-dd"
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
