package com.kglabs28.yapnplay.backend.domain.moderation

/** Reports, ballots and penalties. */
interface ModerationRepository {
    suspend fun insertReport(report: ReportRecord)
    suspend fun resolve(reportId: String, upheld: Boolean, ballots: List<Ballot>, at: Long)
    suspend fun insertPenalty(penalty: Penalty)
    /** The penalty still blocking [userId] at [now], if any. */
    suspend fun activeBlock(userId: String, now: Long): Penalty?
    /** Lifts every active block (a rewarded ad was watched). */
    suspend fun clearBlocks(userId: String, now: Long)
}
