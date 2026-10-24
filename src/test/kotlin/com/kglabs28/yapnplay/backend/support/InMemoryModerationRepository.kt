package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.moderation.Ballot
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Penalty
import com.kglabs28.yapnplay.backend.domain.moderation.ReportRecord

/** Moderation storage in memory; [blocked] users have an active block. */
class InMemoryModerationRepository : ModerationRepository {
    val blocked = mutableSetOf<String>()
    val penalties = mutableListOf<Penalty>()

    override suspend fun insertReport(report: ReportRecord) = Unit
    override suspend fun resolve(reportId: String, upheld: Boolean, ballots: List<Ballot>, at: Long) = Unit

    override suspend fun insertPenalty(penalty: Penalty) {
        penalties += penalty
    }

    override suspend fun activeBlock(userId: String, now: Long): Penalty? =
        if (userId in blocked) Penalty("p", userId, null, 0, Long.MAX_VALUE, now) else null

    override suspend fun clearBlocks(userId: String, now: Long) {
        blocked -= userId
    }

    override suspend fun undoPenalties(reportId: String, now: Long): List<Penalty> =
        penalties.filter { it.reportId == reportId }.onEach { blocked -= it.userId }
}
