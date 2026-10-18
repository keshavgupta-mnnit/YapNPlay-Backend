package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStatus
import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.MatchOutcomesTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [MatchOutcomeStore] on PostgreSQL. */
class ExposedMatchOutcomeStore(private val db: DbQuery) : MatchOutcomeStore {

    override suspend fun started(matchId: String, gameId: String, roomId: String?, startedAt: Long) {
        db.run {
            MatchOutcomesTable.insertIgnore {
                it[this.matchId] = matchId
                it[this.gameId] = gameId
                it[this.roomId] = roomId
                it[this.startedAt] = startedAt
                it[status] = MatchOutcomeStatus.RUNNING.name
                it[errorCount] = 0
            }
        }
    }

    override suspend fun ended(matchId: String, status: MatchOutcomeStatus, endedAt: Long, reason: String?) {
        db.run {
            MatchOutcomesTable.update({
                (MatchOutcomesTable.matchId eq matchId) and (MatchOutcomesTable.status eq MatchOutcomeStatus.RUNNING.name)
            }) {
                it[this.status] = status.name
                it[this.endedAt] = endedAt
                it[this.reason] = reason?.take(REASON_MAX_LENGTH)
            }
        }
    }

    override suspend fun errorSeen(matchId: String) {
        db.run {
            MatchOutcomesTable.update({ MatchOutcomesTable.matchId eq matchId }) {
                it[errorCount] = errorCount + 1
            }
        }
    }

    override suspend fun running(): List<String> = db.run {
        MatchOutcomesTable.selectAll().where { MatchOutcomesTable.status eq MatchOutcomeStatus.RUNNING.name }
            .map { it[MatchOutcomesTable.matchId] }
    }

    private companion object {
        const val REASON_MAX_LENGTH = 500
    }
}
