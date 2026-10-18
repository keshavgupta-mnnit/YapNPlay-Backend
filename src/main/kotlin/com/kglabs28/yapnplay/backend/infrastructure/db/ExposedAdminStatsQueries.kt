package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.admin.AccountType
import com.kglabs28.yapnplay.backend.application.admin.AccountTypeCount
import com.kglabs28.yapnplay.backend.application.admin.AdminStatsQueries
import com.kglabs28.yapnplay.backend.application.admin.DayCount
import com.kglabs28.yapnplay.backend.application.admin.ReportedUser
import com.kglabs28.yapnplay.backend.application.admin.StoredGameCounts
import com.kglabs28.yapnplay.backend.application.admin.StoredModerationCounts
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

    override suspend fun userCountsByType(): List<AccountTypeCount> = db.run {
        rows(USERS_BY_TYPE, listOf(TextColumnType() to AccountType.GUEST)) { AccountTypeCount(it.getString(1), it.getInt(2)) }
            .filter { it.count > 0 }
    }

    override suspend fun usersPerDay(sinceMillis: Long, zone: String): List<DayCount> =
        db.run { perDay(USERS_TABLE, sinceMillis, zone) }

    override suspend fun roomCount(): Int = db.run { single(COUNT_ROOMS) { it.getInt(1) } }

    override suspend fun roomsPerDay(sinceMillis: Long, zone: String): List<DayCount> =
        db.run { perDay(ROOMS_TABLE, sinceMillis, zone) }

    override suspend fun matchesByGame(): List<StoredGameCounts> = db.run {
        rows(MATCHES_BY_GAME) { StoredGameCounts(it.getString(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
    }

    override suspend fun moderationCounts(nowMillis: Long): StoredModerationCounts = db.run {
        val reports = single(REPORT_COUNTS) { intArrayOf(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4)) }
        val penalties = single(PENALTY_COUNTS, listOf(LongColumnType() to nowMillis)) { intArrayOf(it.getInt(1), it.getInt(2)) }
        StoredModerationCounts(
            reports = reports[0],
            upheld = reports[1],
            rejected = reports[2],
            pending = reports[3],
            penalties = penalties[0],
            penaltiesActive = penalties[1],
        )
    }

    override suspend fun mostReported(limit: Int): List<ReportedUser> = db.run {
        rows(MOST_REPORTED, listOf(IntegerColumnType() to limit)) {
            ReportedUser(it.getString(1), it.getString(2), it.getString(3)?.trim(), it.getInt(4), it.getInt(5))
        }
    }

    /** Rows of [table] (a constant, never input) by the calendar day of `created_at` in [zone]. */
    private fun Transaction.perDay(table: String, sinceMillis: Long, zone: String): List<DayCount> = rows(
        """
        SELECT to_char(to_timestamp(created_at / 1000.0) AT TIME ZONE ?, 'YYYY-MM-DD') AS day, count(*)
        FROM $table
        WHERE created_at >= ?
        GROUP BY day
        ORDER BY day
        """,
        listOf(TextColumnType() to zone, LongColumnType() to sinceMillis),
    ) { DayCount(it.getString(1), it.getInt(2)) }

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
        const val USERS_BY_TYPE = "SELECT ?, count(*) FROM users"
        const val COUNT_ROOMS = "SELECT count(*) FROM play_rooms"
        const val MATCHES_BY_GAME = """
            SELECT m.game_id,
                   count(*),
                   count(*) FILTER (WHERE m.room_id IS NULL),
                   coalesce(sum(h.humans), 0)
            FROM match_results m
            LEFT JOIN (SELECT match_id, count(user_id) AS humans FROM match_result_seats GROUP BY match_id) h
                   ON h.match_id = m.id
            GROUP BY m.game_id
            ORDER BY count(*) DESC, m.game_id
            """
        const val REPORT_COUNTS = """
            SELECT count(*),
                   count(*) FILTER (WHERE upheld = TRUE),
                   count(*) FILTER (WHERE upheld = FALSE),
                   count(*) FILTER (WHERE resolved_at IS NULL)
            FROM reports
            """
        const val PENALTY_COUNTS =
            "SELECT count(*), count(*) FILTER (WHERE cleared_at IS NULL AND blocked_until > ?) FROM penalties"
        const val MOST_REPORTED = """
            SELECT u.id, u.display_name, u.user_code, count(*), count(*) FILTER (WHERE r.upheld = TRUE)
            FROM reports r
            JOIN users u ON u.id = r.reported_id
            GROUP BY u.id, u.display_name, u.user_code
            ORDER BY count(*) DESC, count(*) FILTER (WHERE r.upheld = TRUE) DESC, u.display_name
            LIMIT ?
            """
    }
}
