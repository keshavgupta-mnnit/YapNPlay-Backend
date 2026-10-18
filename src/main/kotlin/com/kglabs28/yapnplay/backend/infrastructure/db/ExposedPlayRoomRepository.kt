package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomMembership
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PlayRoomMembersTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PlayRoomsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [PlayRoomRepository] on PostgreSQL. */
class ExposedPlayRoomRepository(private val db: DbQuery) : PlayRoomRepository {

    override suspend fun insertWithCreator(room: PlayRoom, creatorJoinedAt: Long): Boolean = try {
        db.run {
            PlayRoomsTable.insert {
                it[id] = room.id.toUuid()
                it[code] = room.code
                it[name] = room.name
                it[description] = room.description
                it[icon] = room.icon
                it[createdBy] = room.createdBy.toUuid()
                it[leaderId] = room.leaderId.toUuid()
                it[adminId] = room.adminId.toUuid()
                it[selectedGame] = room.selectedGame
                it[botFill] = room.botFill
                it[createdAt] = room.createdAt
            }
            PlayRoomMembersTable.insert {
                it[roomId] = room.id.toUuid()
                it[userId] = room.createdBy.toUuid()
                it[joinedAt] = creatorJoinedAt
            }
        }
        true
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) false else throw e
    }

    override suspend fun findById(roomId: String): PlayRoom? {
        val uuid = roomId.toUuidOrNull() ?: return null
        return db.run { PlayRoomsTable.selectAll().where { PlayRoomsTable.id eq uuid }.singleOrNull()?.toRoom() }
    }

    override suspend fun findByCode(code: String): PlayRoom? =
        db.run { PlayRoomsTable.selectAll().where { PlayRoomsTable.code eq code }.singleOrNull()?.toRoom() }

    override suspend fun members(roomId: String): List<PlayRoomMembership> = db.run {
        PlayRoomMembersTable.join(UsersTable, JoinType.INNER, PlayRoomMembersTable.userId, UsersTable.id)
            .selectAll()
            .where { PlayRoomMembersTable.roomId eq roomId.toUuid() }
            .orderBy(PlayRoomMembersTable.joinedAt)
            .map {
                PlayRoomMembership(
                    roomId = roomId,
                    userId = it[PlayRoomMembersTable.userId].toString(),
                    displayName = it[UsersTable.displayName],
                    joinedAt = it[PlayRoomMembersTable.joinedAt],
                )
            }
    }

    override suspend fun roomsOf(userId: String): List<PlayRoom> = db.run {
        PlayRoomsTable.join(PlayRoomMembersTable, JoinType.INNER, PlayRoomsTable.id, PlayRoomMembersTable.roomId)
            .selectAll()
            .where { PlayRoomMembersTable.userId eq userId.toUuid() }
            .orderBy(PlayRoomMembersTable.joinedAt)
            .map { it.toRoom() }
    }

    override suspend fun membershipCount(userId: String): Int = db.run {
        PlayRoomMembersTable.selectAll().where { PlayRoomMembersTable.userId eq userId.toUuid() }.count().toInt()
    }

    override suspend fun memberCount(roomId: String): Int = db.run {
        PlayRoomMembersTable.selectAll().where { PlayRoomMembersTable.roomId eq roomId.toUuid() }.count().toInt()
    }

    override suspend fun addMember(roomId: String, userId: String, joinedAt: Long) {
        db.run {
            PlayRoomMembersTable.insert {
                it[PlayRoomMembersTable.roomId] = roomId.toUuid()
                it[PlayRoomMembersTable.userId] = userId.toUuid()
                it[PlayRoomMembersTable.joinedAt] = joinedAt
            }
        }
    }

    override suspend fun removeMember(roomId: String, userId: String) {
        db.run {
            PlayRoomMembersTable.deleteWhere {
                (PlayRoomMembersTable.roomId eq roomId.toUuid()) and (PlayRoomMembersTable.userId eq userId.toUuid())
            }
        }
    }

    override suspend fun updateRoles(roomId: String, leaderId: String, adminId: String) {
        db.run {
            PlayRoomsTable.update({ PlayRoomsTable.id eq roomId.toUuid() }) {
                it[PlayRoomsTable.leaderId] = leaderId.toUuid()
                it[PlayRoomsTable.adminId] = adminId.toUuid()
            }
        }
    }

    override suspend fun updateGame(roomId: String, selectedGame: String?) {
        db.run {
            PlayRoomsTable.update({ PlayRoomsTable.id eq roomId.toUuid() }) { it[PlayRoomsTable.selectedGame] = selectedGame }
        }
    }

    override suspend fun updateBotFill(roomId: String, botFill: Boolean) {
        db.run {
            PlayRoomsTable.update({ PlayRoomsTable.id eq roomId.toUuid() }) { it[PlayRoomsTable.botFill] = botFill }
        }
    }

    override suspend fun delete(roomId: String) {
        db.run { PlayRoomsTable.deleteWhere { PlayRoomsTable.id eq roomId.toUuid() } }
    }

    private fun ResultRow.toRoom() = PlayRoom(
        id = this[PlayRoomsTable.id].toString(),
        code = this[PlayRoomsTable.code],
        name = this[PlayRoomsTable.name],
        description = this[PlayRoomsTable.description],
        icon = this[PlayRoomsTable.icon],
        createdBy = this[PlayRoomsTable.createdBy].toString(),
        leaderId = this[PlayRoomsTable.leaderId].toString(),
        adminId = this[PlayRoomsTable.adminId].toString(),
        selectedGame = this[PlayRoomsTable.selectedGame],
        botFill = this[PlayRoomsTable.botFill],
        createdAt = this[PlayRoomsTable.createdAt],
    )

    private companion object {
        /** PostgreSQL's code for "duplicate key" (the room code was taken). */
        const val UNIQUE_VIOLATION = "23505"
    }
}
