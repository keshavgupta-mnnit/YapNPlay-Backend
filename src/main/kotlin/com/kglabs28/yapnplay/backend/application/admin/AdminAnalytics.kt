package com.kglabs28.yapnplay.backend.application.admin

import kotlinx.serialization.Serializable

/**
 * `GET /admin/analytics`: everything in one period, for the dashboard's
 * Analytics view. Not part of the app contract. [timeline] has one point per
 * hour ([BUCKET_HOUR], periods up to two days) or per day, empty ones included.
 */
@Serializable
data class AdminAnalytics(
    val generatedAt: Long,
    val timeZone: String,
    /** The period (epoch millis, end left out); for "all time" from the first account to now. */
    val rangeFrom: Long,
    val rangeTo: Long,
    val allTime: Boolean,
    val bucket: String,
    val summary: AnalyticsSummary,
    val timeline: List<AnalyticsPoint>,
    /** New accounts in the period by where they were made. */
    val sources: List<SourceCount>,
    /** Every account ever, by where it was made. */
    val allTimeSources: List<SourceCount>,
    val games: List<GameAnalytics>,
) {
    companion object {
        const val BUCKET_HOUR = "hour"
        const val BUCKET_DAY = "day"
    }
}

/** The period's headline numbers. */
@Serializable
data class AnalyticsSummary(
    val newPlayers: Int,
    /** Different people who played a match that ended in the period. */
    val activePlayers: Int,
    val matchesStarted: Int,
    val matchesCompleted: Int,
    /** Ended by an error or a server restart before a result. */
    val matchesStopped: Int,
    /** Started in the period and still going. */
    val matchesRunning: Int,
    /** Player seats (not CPUs) over the matches that ended. */
    val humanSeats: Int,
    /** Of those, players who quit before the end. */
    val playersLeft: Int,
    val minutesPlayed: Long,
)

/** One hour or day of [AdminAnalytics.timeline]. */
@Serializable
data class AnalyticsPoint(
    /** `YYYY-MM-DD` or `YYYY-MM-DD HH:00`, in [AdminAnalytics.timeZone]. */
    val label: String,
    val newPlayers: Int = 0,
    val activePlayers: Int = 0,
    val matchesStarted: Int = 0,
    val matchesCompleted: Int = 0,
    val matchesStopped: Int = 0,
)

/** Accounts made on [platform] (`android` app, `web`, `unknown`) and, for the web, [os] ("" when not known). */
@Serializable
data class SourceCount(val platform: String, val os: String, val count: Int)

/** One game's audience and how its matches went, in the period. */
@Serializable
data class GameAnalytics(
    val gameId: String,
    val displayName: String,
    val matchesStarted: Int,
    val matchesCompleted: Int,
    val matchesStopped: Int,
    /** Different people who played it. */
    val players: Int,
    val humanSeats: Int,
    /** Players who quit a match of it before the end (its drop rate is this over [humanSeats]). */
    val playersLeft: Int,
    val quickPlayMatches: Int,
    val roomMatches: Int,
)

/** A count in one hour or day, as stored. */
data class BucketCount(val label: String, val count: Int)

/** Matches started in one hour or day, by how they ended, as stored. */
data class BucketMatches(val label: String, val started: Int, val completed: Int, val stopped: Int)

/** One game's matches started in the period, by how they ended, as stored. */
data class StoredGameOutcomes(val gameId: String, val started: Int, val completed: Int, val stopped: Int)

/** One game's finished matches in the period: its players, seats, quitters and Quick Play share, as stored. */
data class StoredGamePlayers(
    val gameId: String,
    val players: Int,
    val humanSeats: Int,
    val playersLeft: Int,
    val quickPlayMatches: Int,
    val finishedMatches: Int,
)
