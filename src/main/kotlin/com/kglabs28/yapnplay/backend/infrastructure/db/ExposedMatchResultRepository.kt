package com.kglabs28.yapnplay.backend.infrastructure.db

import org.jetbrains.exposed.sql.and
import com.kglabs28.yapnplay.backend.domain.match.RoomTotals
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PlayRoomsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import com.kglabs28.yapnplay.backend.domain.match.PlayedMatch
import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat
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
                    it[displayName] = seat.displayName.take(NAME_MAX)
                    it[avatarId] = seat.avatarId.take(AVATAR_MAX)
                    it[coinsWon] = seat.coinsWon
                    it[completionCoins] = seat.completionCoins
                    it[stake] = seat.stake
                }
            }
            xpByUser.forEach { (user, xp) ->
                UsersTable.update({ UsersTable.id eq user.toUuid() }) { it[UsersTable.xp] = UsersTable.xp + xp }
            }
        }
    }

    override suspend fun history(userId: String, limit: Int): List<PlayedMatch> = db.run {
        val user = userId.toUuidOrNull() ?: return@run emptyList()
        val matches = (MatchResultsTable innerJoin MatchResultSeatsTable)
            .selectAll()
            .where { MatchResultSeatsTable.userId eq user }
            .orderBy(MatchResultsTable.endedAt, SortOrder.DESC)
            .limit(limit)
            .toList()
        val ids = matches.map { it[MatchResultsTable.id] }
        val seats = MatchResultSeatsTable.selectAll()
            .where { MatchResultSeatsTable.matchId inList ids }
            .groupBy({ it[MatchResultSeatsTable.matchId] }) { row ->
                FinishedSeat(
                    seat = row[MatchResultSeatsTable.seat],
                    userId = row[MatchResultSeatsTable.userId]?.toString(),
                    place = row[MatchResultSeatsTable.place],
                    xp = row[MatchResultSeatsTable.xpAwarded],
                    displayName = row[MatchResultSeatsTable.displayName],
                    avatarId = row[MatchResultSeatsTable.avatarId],
                    coinsWon = row[MatchResultSeatsTable.coinsWon],
                    completionCoins = row[MatchResultSeatsTable.completionCoins],
                    stake = row[MatchResultSeatsTable.stake],
                )
            }
        val roomIds = matches.mapNotNull { it[MatchResultsTable.roomId] }.distinct()
        val roomNames = if (roomIds.isEmpty()) emptyMap() else PlayRoomsTable.selectAll().where { PlayRoomsTable.id inList roomIds }
            .associate { it[PlayRoomsTable.id] to it[PlayRoomsTable.name] }
        matches.map { row ->
            val id = row[MatchResultsTable.id]
            PlayedMatch(
                matchId = id.toString(),
                gameId = row[MatchResultsTable.gameId],
                roomMatch = row[MatchResultsTable.roomId] != null,
                roomName = row[MatchResultsTable.roomId]?.let { roomNames[it] }.orEmpty(),
                startedAt = row[MatchResultsTable.startedAt],
                endedAt = row[MatchResultsTable.endedAt],
                seats = seats[id].orEmpty().sortedBy { it.place },
            )
        }
    }

    override suspend fun roomTotals(roomId: String): Map<String, RoomTotals> {
        val room = roomId.toUuidOrNull() ?: return emptyMap()
        return db.run {
            (MatchResultsTable innerJoin MatchResultSeatsTable).selectAll()
                .where { (MatchResultsTable.roomId eq room) and MatchResultSeatsTable.userId.isNotNull() }
                .groupBy { it[MatchResultSeatsTable.userId].toString() }
                .mapValues { (_, rows) ->
                    RoomTotals(
                        played = rows.size,
                        wins = rows.count { it[MatchResultSeatsTable.place] == 1 },
                        coins = rows.sumOf { it[MatchResultSeatsTable.coinsWon] + it[MatchResultSeatsTable.completionCoins] },
                    )
                }
        }
    }

    private companion object {
        const val NAME_MAX = 64
        const val AVATAR_MAX = 40
    }
}
