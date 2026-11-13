package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.voice.RelayDay
import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.statements.StatementType

/** `voice_relay_usage` (V31): one row a month; `voice_relay_daily` (V33): one per person per day. */
class ExposedRelayUsageStore(private val db: DbQuery) : RelayUsageStore {

    override suspend fun add(month: String, bytes: Long, at: Long) {
        db.run {
            exec(
                """
                INSERT INTO voice_relay_usage (month, bytes, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (month) DO UPDATE SET bytes = voice_relay_usage.bytes + EXCLUDED.bytes, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
                listOf(text(MONTH_LENGTH) to month, LongColumnType() to bytes, LongColumnType() to at),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun bytes(month: String): Long = db.run {
        rows("SELECT bytes FROM voice_relay_usage WHERE month = ?", listOf(text(MONTH_LENGTH) to month)) { it.getLong(1) }
            .firstOrNull() ?: 0L
    }

    override suspend fun addDaily(day: String, userId: String, bytes: Long) {
        db.run {
            exec(
                """
                INSERT INTO voice_relay_daily (day, user_id, bytes) VALUES (?, ?, ?)
                ON CONFLICT (day, user_id) DO UPDATE SET bytes = voice_relay_daily.bytes + EXCLUDED.bytes
                """.trimIndent(),
                listOf(text(DAY_LENGTH) to day, text(USER_ID_LENGTH) to userId, LongColumnType() to bytes),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun daily(fromDay: String, toDay: String): List<RelayDay> = db.run {
        rows(
            "SELECT day, count(*), sum(bytes) FROM voice_relay_daily WHERE day >= ? AND day <= ? GROUP BY day ORDER BY day",
            listOf(text(DAY_LENGTH) to fromDay, text(DAY_LENGTH) to toDay),
        ) { RelayDay(it.getString(1), it.getInt(2), it.getLong(3)) }
    }

    override suspend fun users(fromDay: String, toDay: String): Int = db.run {
        single(
            "SELECT count(DISTINCT user_id) FROM voice_relay_daily WHERE day >= ? AND day <= ?",
            listOf(text(DAY_LENGTH) to fromDay, text(DAY_LENGTH) to toDay),
        ) { it.getInt(1) }
    }

    private fun text(length: Int) = VarCharColumnType(length)

    private companion object {
        const val MONTH_LENGTH = 7
        const val DAY_LENGTH = 10
        const val USER_ID_LENGTH = 64
    }
}
