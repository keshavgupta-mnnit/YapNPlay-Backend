package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.MatchResultSeatsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.MatchResultsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update

/** [MatchResultRepository] on PostgreSQL: results and XP in one transaction. */
class ExposedMatchResultRepository(private val db: DbQuery) : MatchResultRepository {

    override suspend fun save(match: FinishedMatch, xpByUser: Map<String, Int>) {
        db.run {
            MatchResultsTable.insert {
                it[id] = match.matchId.toUuid()
                it[gameId] = match.gameId
                it[roomId] = match.roomId?.toUuidOrNull()
                it[startedAt] = match.startedAt
                it[endedAt] = match.endedAt
            }
            match.seats.forEach { seat ->
                MatchResultSeatsTable.insert {
                    it[matchId] = match.matchId.toUuid()
                    it[this.seat] = seat.seat
                    it[userId] = seat.userId?.toUuidOrNull()
                    it[place] = seat.place
                    it[xpAwarded] = seat.userId?.let { id -> xpByUser[id] } ?: 0
                }
            }
            xpByUser.forEach { (user, xp) ->
                UsersTable.update({ UsersTable.id eq user.toUuid() }) { it[UsersTable.xp] = UsersTable.xp + xp }
            }
        }
    }
}
