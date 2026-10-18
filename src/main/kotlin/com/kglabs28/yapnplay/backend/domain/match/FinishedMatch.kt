package com.kglabs28.yapnplay.backend.domain.match

/** A match that just ended, for results and XP. Times are epoch millis. */
data class FinishedMatch(
    val matchId: String,
    val gameId: String,
    val roomId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val seats: List<FinishedSeat>,
)

/** [userId] is null for a CPU seat. [place] is 1-based. */
data class FinishedSeat(
    val seat: Int,
    val userId: String?,
    val place: Int,
)
