package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.admin.AdminAnalyticsQueries
import com.kglabs28.yapnplay.backend.application.admin.BucketCount
import com.kglabs28.yapnplay.backend.application.admin.BucketMatches
import com.kglabs28.yapnplay.backend.application.admin.SourceCount
import com.kglabs28.yapnplay.backend.application.admin.StatsRange
import com.kglabs28.yapnplay.backend.application.admin.StoredGameOutcomes
import com.kglabs28.yapnplay.backend.application.admin.StoredGamePlayers
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.TextColumnType

/** [AdminAnalyticsQueries] as plain aggregate SQL ([rows] / [single]). Every statement is a SELECT. */
class ExposedAdminAnalyticsQueries(private val db: DbQuery) : AdminAnalyticsQueries {

    override suspend fun newPlayers(range: StatsRange, zone: String, format: String): List<BucketCount> = db.run {
        rows(NEW_PLAYERS, bucketArgs(zone, format) + range.args()) { BucketCount(it.getString(1), it.getInt(2)) }
    }

    override suspend fun activePlayers(range: StatsRange, zone: String, format: String): List<BucketCount> = db.run {
        rows(ACTIVE_PLAYERS, bucketArgs(zone, format) + range.args()) { BucketCount(it.getString(1), it.getInt(2)) }
    }

    override suspend fun matches(range: StatsRange, zone: String, format: String): List<BucketMatches> = db.run {
        rows(MATCHES, bucketArgs(zone, format) + range.args()) { BucketMatches(it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
    }

    override suspend fun sources(range: StatsRange): List<SourceCount> = db.run {
        rows(SOURCES, range.args()) { SourceCount(it.getString(1), it.getString(2), it.getInt(3)) }
    }

    override suspend fun gameOutcomes(range: StatsRange): List<StoredGameOutcomes> = db.run {
        rows(GAME_OUTCOMES, range.args()) { StoredGameOutcomes(it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
    }

    override suspend fun gamePlayers(range: StatsRange): List<StoredGamePlayers> = db.run {
        rows(GAME_PLAYERS, range.args()) {
            StoredGamePlayers(it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4), it.getInt(5), it.getInt(6))
        }
    }

    override suspend fun firstAccountAt(): Long? = db.run {
        single(FIRST_ACCOUNT) { rs -> rs.getLong(1).takeUnless { rs.wasNull() } }
    }

    /** The bucket's two `?`s: the time zone and the `to_char` format. */
    private fun bucketArgs(zone: String, format: String): List<Pair<IColumnType<*>, Any?>> =
        listOf(TextColumnType() to zone, TextColumnType() to format)

    private companion object {
        const val NEW_PLAYERS = """
            SELECT to_char(to_timestamp(created_at / 1000.0) AT TIME ZONE ?, ?) AS b, count(*)
            FROM users
            WHERE created_at >= ? AND created_at < ?
            GROUP BY b
            """
        const val ACTIVE_PLAYERS = """
            SELECT to_char(to_timestamp(m.ended_at / 1000.0) AT TIME ZONE ?, ?) AS b, count(DISTINCT s.user_id)
            FROM match_result_seats s
            JOIN match_results m ON m.id = s.match_id
            WHERE s.user_id IS NOT NULL AND m.ended_at >= ? AND m.ended_at < ?
            GROUP BY b
            """
        const val MATCHES = """
            SELECT to_char(to_timestamp(started_at / 1000.0) AT TIME ZONE ?, ?) AS b,
                   count(*),
                   count(*) FILTER (WHERE status = 'COMPLETED'),
                   count(*) FILTER (WHERE status LIKE 'ABORTED%')
            FROM match_outcomes
            WHERE started_at >= ? AND started_at < ?
            GROUP BY b
            """
        const val SOURCES = """
            SELECT coalesce(platform, 'unknown'), coalesce(device_os, ''), count(*)
            FROM users
            WHERE created_at >= ? AND created_at < ?
            GROUP BY 1, 2
            ORDER BY 3 DESC, 1, 2
            """
        const val GAME_OUTCOMES = """
            SELECT game_id,
                   count(*),
                   count(*) FILTER (WHERE status = 'COMPLETED'),
                   count(*) FILTER (WHERE status LIKE 'ABORTED%')
            FROM match_outcomes
            WHERE started_at >= ? AND started_at < ?
            GROUP BY game_id
            """
        const val GAME_PLAYERS = """
            SELECT m.game_id,
                   count(DISTINCT s.user_id),
                   count(s.user_id),
                   count(*) FILTER (WHERE s.left_match AND s.user_id IS NOT NULL),
                   count(DISTINCT m.id) FILTER (WHERE m.room_id IS NULL),
                   count(DISTINCT m.id)
            FROM match_results m
            LEFT JOIN match_result_seats s ON s.match_id = m.id
            WHERE m.ended_at >= ? AND m.ended_at < ?
            GROUP BY m.game_id
            """
        const val FIRST_ACCOUNT = "SELECT min(created_at) FROM users"
    }
}
