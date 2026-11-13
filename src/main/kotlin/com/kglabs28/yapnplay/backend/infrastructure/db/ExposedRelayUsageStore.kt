package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.voice.RelayUsageStore
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.statements.StatementType

/** `voice_relay_usage` (V31): one row a month, added to in place. */
class ExposedRelayUsageStore(private val db: DbQuery) : RelayUsageStore {

    override suspend fun add(month: String, bytes: Long, at: Long) {
        db.run {
            exec(
                """
                INSERT INTO voice_relay_usage (month, bytes, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (month) DO UPDATE SET bytes = voice_relay_usage.bytes + EXCLUDED.bytes, updated_at = EXCLUDED.updated_at
                """.trimIndent(),
                listOf(VarCharColumnType(MONTH_LENGTH) to month, LongColumnType() to bytes, LongColumnType() to at),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun bytes(month: String): Long = db.run {
        rows("SELECT bytes FROM voice_relay_usage WHERE month = ?", listOf(VarCharColumnType(MONTH_LENGTH) to month)) { it.getLong(1) }
            .firstOrNull() ?: 0L
    }

    private companion object {
        const val MONTH_LENGTH = 7
    }
}
