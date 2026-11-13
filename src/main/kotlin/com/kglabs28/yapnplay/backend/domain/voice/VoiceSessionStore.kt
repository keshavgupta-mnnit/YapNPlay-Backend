package com.kglabs28.yapnplay.backend.domain.voice

/** One stretch [userId] spent in a voice room (epoch ms). */
data class VoiceSession(val userId: String, val startedAt: Long, val endedAt: Long)

/** Voice use in a period: different people, sessions and seconds in all. */
data class VoiceUsageTotals(val users: Int, val sessions: Int, val seconds: Long)

/** One calendar day's voice use (`YYYY-MM-DD` in the dashboard's time zone). */
data class VoiceUsageDay(val day: String, val users: Int, val sessions: Int, val seconds: Long)

/** Voice sessions, written as they end, read by the admin dashboard. */
interface VoiceSessionStore {
    suspend fun add(session: VoiceSession)

    /** Sessions that started in [fromMillis, untilMillis). */
    suspend fun totals(fromMillis: Long, untilMillis: Long): VoiceUsageTotals

    suspend fun perDay(fromMillis: Long, untilMillis: Long, zone: String): List<VoiceUsageDay>
}
