package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.voice.VoiceSession
import com.kglabs28.yapnplay.backend.domain.voice.VoiceSessionStore
import com.kglabs28.yapnplay.backend.domain.voice.VoiceUsageDay
import com.kglabs28.yapnplay.backend.domain.voice.VoiceUsageTotals
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.statements.StatementType

/** `voice_sessions` (V32). */
class ExposedVoiceSessionStore(private val db: DbQuery) : VoiceSessionStore {

    override suspend fun add(session: VoiceSession) {
        db.run {
            exec(
                "INSERT INTO voice_sessions (user_id, started_at, ended_at, seconds) VALUES (?, ?, ?, ?)",
                listOf(
                    VarCharColumnType(USER_ID_LENGTH) to session.userId,
                    LongColumnType() to session.startedAt,
                    LongColumnType() to session.endedAt,
                    IntegerColumnType() to ((session.endedAt - session.startedAt) / MILLIS_PER_SECOND).toInt(),
                ),
                StatementType.INSERT,
            )
        }
    }

    override suspend fun totals(fromMillis: Long, untilMillis: Long): VoiceUsageTotals = db.run {
        single(
            """
            SELECT count(DISTINCT user_id), count(*), coalesce(sum(seconds), 0)
            FROM voice_sessions WHERE started_at >= ? AND started_at < ?
            """,
            listOf(LongColumnType() to fromMillis, LongColumnType() to untilMillis),
        ) { VoiceUsageTotals(it.getInt(1), it.getInt(2), it.getLong(3)) }
    }

    override suspend fun perDay(fromMillis: Long, untilMillis: Long, zone: String): List<VoiceUsageDay> = db.run {
        rows(
            """
            SELECT to_char(to_timestamp(started_at / 1000.0) AT TIME ZONE ?, 'YYYY-MM-DD') AS day,
                   count(DISTINCT user_id), count(*), coalesce(sum(seconds), 0)
            FROM voice_sessions WHERE started_at >= ? AND started_at < ?
            GROUP BY day ORDER BY day
            """,
            listOf(TextColumnType() to zone, LongColumnType() to fromMillis, LongColumnType() to untilMillis),
        ) { VoiceUsageDay(it.getString(1), it.getInt(2), it.getInt(3), it.getLong(4)) }
    }

    private companion object {
        const val USER_ID_LENGTH = 64
        const val MILLIS_PER_SECOND = 1_000L
    }
}
