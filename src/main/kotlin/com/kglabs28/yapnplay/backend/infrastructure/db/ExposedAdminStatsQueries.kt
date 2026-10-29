package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.admin.StoredAppTime
import com.kglabs28.yapnplay.backend.application.admin.StoredMatchTotals
import com.kglabs28.yapnplay.backend.application.admin.AccountType
import com.kglabs28.yapnplay.backend.application.admin.AccountTypeCount
import com.kglabs28.yapnplay.backend.application.admin.AdminStatsQueries
import com.kglabs28.yapnplay.backend.application.admin.DayCount
import com.kglabs28.yapnplay.backend.application.admin.ReportedUser
import com.kglabs28.yapnplay.backend.application.admin.StoredGameCounts
import com.kglabs28.yapnplay.backend.application.admin.StoredModerationCounts
import com.kglabs28.yapnplay.backend.application.admin.StatsRange
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.statements.StatementType
import java.sql.ResultSet

/**
 * [AdminStatsQueries] as plain aggregate SQL (GROUP BY and FILTER read
 * more clearly than the DSL here). Every statement is a SELECT.
 */
class ExposedAdminStatsQueries(private val db: DbQuery) : AdminStatsQueries {

    override suspend fun userCountsByType(range: StatsRange): List<AccountTypeCount> = db.run {
        rows(USERS_BY_TYPE, listOf(TextColumnType() to AccountType.GUEST) + range.args()) { AccountTypeCount(it.getString(1), it.getInt(2)) }
            .filter { it.count > 0 }
    }

    override suspend fun usersPerDay(sinceMillis: Long, zone: String, untilMillis: Long): List<DayCount> =
        db.run { perDay(USERS_TABLE, sinceMillis, untilMillis, zone) }

    override suspend fun roomCount(range: StatsRange): Int = db.run { single(COUNT_ROOMS, range.args()) { it.getInt(1) } }

    override suspend fun roomsPerDay(sinceMillis: Long, zone: String, untilMillis: Long): List<DayCount> =
        db.run { perDay(ROOMS_TABLE, sinceMillis, untilMillis, zone) }

    override suspend fun matchesByGame(range: StatsRange): List<StoredGameCounts> = db.run {
        rows(MATCHES_BY_GAME, range.args()) { StoredGameCounts(it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
    }

    override suspend fun moderationCounts(nowMillis: Long, range: StatsRange): StoredModerationCounts = db.run {
        val reports = single(REPORT_COUNTS, range.args()) { intArrayOf(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
        val penalties = single(PENALTY_COUNTS, listOf(LongColumnType() to nowMillis) + range.args()) { intArrayOf(it.getInt(1), it.getInt(2)) }
        StoredModerationCounts(
            reports = reports[0],
            upheld = reports[1],
            rejected = reports[2],
            pending = reports[3],
            penalties = penalties[0],
            penaltiesActive = penalties[1],
        )
    }

    override suspend fun mostReported(limit: Int, range: StatsRange): List<ReportedUser> = db.run {
        rows(MOST_REPORTED, range.args() + (IntegerColumnType() to limit)) {
            ReportedUser(it.getString(1), it.getString(2), it.getString(3)?.trim(), it.getInt(4), it.getInt(5))
        }
    }

    override suspend fun matchTotals(range: StatsRange): StoredMatchTotals = db.run {
        single(MATCH_TOTALS, range.args()) { StoredMatchTotals(it.getInt(1), it.getInt(2), it.getLong(3)) }
    }

    override suspend fun playersLeft(range: StatsRange): Int = db.run { single(PLAYERS_LEFT, range.args()) { it.getInt(1) } }

    override suspend fun appTime(): StoredAppTime = db.run {
        single(APP_TIME) { StoredAppTime(it.getLong(1), it.getInt(2)) }
    }

    /** Rows of [table] (a constant, never input) by the calendar day of `created_at` in [zone]. */
    private fun Transaction.perDay(table: String, sinceMillis: Long, untilMillis: Long, zone: String): List<DayCount> = rows(
        """
        SELECT to_char(to_timestamp(created_at / 1000.0) AT TIME ZONE ?, 'YYYY-MM-DD') AS day, count(*)
        FROM $table
        WHERE created_at >= ? AND created_at < ?
        GROUP BY day
        ORDER BY day
        """,
        listOf(TextColumnType() to zone, LongColumnType() to sinceMillis, LongColumnType() to untilMillis),
    ) { DayCount(it.getString(1), it.getInt(2)) }

    /** The two `?`s of a `created_at >= ? AND created_at < ?` filter: from (included), to (left out). */
    private fun StatsRange.args(): List<Pair<IColumnType<*>, Any?>> = listOf(LongColumnType() to fromMillis, LongColumnType() to toMillis)

    private fun <T> Transaction.rows(
        sql: String,
        args: List<Pair<IColumnType<*>, Any?>> = emptyList(),
        map: (ResultSet) -> T,
    ): List<T> = exec(sql.trimIndent(), args, StatementType.SELECT) { rs ->
        buildList { while (rs.next()) add(map(rs)) }
    }.orEmpty()

    private fun <T> Transaction.single(
        sql: String,
        args: List<Pair<IColumnType<*>, Any?>> = emptyList(),
        map: (ResultSet) -> T,
    ): T = rows(sql, args, map).first()

    private companion object {
        const val USERS_TABLE = "users"
        const val ROOMS_TABLE = "play_rooms"
        /**
         * There is no sign-in type column yet: every account is a guest. When
         * Google sign-in arrives this becomes `SELECT account_type, count(*) ... GROUP BY account_type`.
         */
        const val USERS_BY_TYPE = "SELECT ?, count(*) FROM users WHERE created_at >= ? AND created_at < ?"
        const val COUNT_ROOMS = "SELECT count(*) FROM play_rooms WHERE created_at >= ? AND created_at < ?"
        const val MATCHES_BY_GAME = """
            SELECT m.game_id,
                   count(*),
                   count(*) FILTER (WHERE m.room_id IS NULL),
                   coalesce(sum(h.humans), 0)
            FROM match_results m
            LEFT JOIN (SELECT match_id, count(user_id) AS humans FROM match_result_seats GROUP BY match_id) h
                   ON h.match_id = m.id
            WHERE m.ended_at >= ? AND m.ended_at < ?
            GROUP BY m.game_id
            ORDER BY count(*) DESC, m.game_id
            """
        const val REPORT_COUNTS = """
            SELECT count(*),
                   count(*) FILTER (WHERE upheld = TRUE),
                   count(*) FILTER (WHERE upheld = FALSE),
                   count(*) FILTER (WHERE resolved_at IS NULL)
            FROM reports
            WHERE created_at >= ? AND created_at < ?
            """
        const val MATCH_TOTALS = """
            SELECT count(*) FILTER (WHERE status = 'COMPLETED'),
                   count(*) FILTER (WHERE status LIKE 'ABORTED%'),
                   coalesce(sum(ended_at - started_at) FILTER (WHERE status = 'COMPLETED' AND ended_at IS NOT NULL), 0)
            FROM match_outcomes
            WHERE started_at >= ? AND started_at < ?
            """
        const val PLAYERS_LEFT = """
            SELECT count(*)
            FROM match_result_seats s
            JOIN match_results m ON m.id = s.match_id
            WHERE s.left_match AND m.ended_at >= ? AND m.ended_at < ?
            """
        const val APP_TIME = "SELECT coalesce(sum(online_ms), 0), count(*) FILTER (WHERE online_ms > 0) FROM users"
        const val PENALTY_COUNTS =
            "SELECT count(*), count(*) FILTER (WHERE cleared_at IS NULL AND blocked_until > ?) FROM penalties WHERE created_at >= ? AND created_at < ?"
        const val MOST_REPORTED = """
            SELECT u.id, u.display_name, u.user_code, count(*), count(*) FILTER (WHERE r.upheld = TRUE)
            FROM reports r
            JOIN users u ON u.id = r.reported_id
            WHERE r.created_at >= ? AND r.created_at < ?
            GROUP BY u.id, u.display_name, u.user_code
            ORDER BY count(*) DESC, count(*) FILTER (WHERE r.upheld = TRUE) DESC, u.display_name
            LIMIT ?
            """
    }
}
