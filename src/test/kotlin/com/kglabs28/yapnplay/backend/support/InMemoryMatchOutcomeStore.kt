package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStatus
import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStore

/** Outcome rows in a map, for actor tests. */
class InMemoryMatchOutcomeStore : MatchOutcomeStore {
    data class Row(
        val gameId: String,
        val roomId: String?,
        val startedAt: Long,
        val status: MatchOutcomeStatus = MatchOutcomeStatus.RUNNING,
        val endedAt: Long? = null,
        val reason: String? = null,
        val errorCount: Int = 0,
    )

    val rows = mutableMapOf<String, Row>()

    override suspend fun started(matchId: String, gameId: String, roomId: String?, startedAt: Long) {
        rows.putIfAbsent(matchId, Row(gameId, roomId, startedAt))
    }

    override suspend fun ended(matchId: String, status: MatchOutcomeStatus, endedAt: Long, reason: String?) {
        val row = rows[matchId] ?: return
        if (row.status == MatchOutcomeStatus.RUNNING) rows[matchId] = row.copy(status = status, endedAt = endedAt, reason = reason)
    }

    override suspend fun errorSeen(matchId: String) {
        rows[matchId]?.let { rows[matchId] = it.copy(errorCount = it.errorCount + 1) }
    }

    override suspend fun running(): List<String> = rows.filterValues { it.status == MatchOutcomeStatus.RUNNING }.keys.toList()
}
