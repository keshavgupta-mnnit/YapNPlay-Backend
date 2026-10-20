package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.rooms.RoomInvite
import com.kglabs28.yapnplay.backend.domain.rooms.RoomInviteRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.RoomInvitesTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

/** [RoomInviteRepository] on PostgreSQL. */
class ExposedRoomInviteRepository(private val db: DbQuery) : RoomInviteRepository {

    override suspend fun insert(invite: RoomInvite): Boolean = db.run {
        RoomInvitesTable.insertIgnore {
            it[id] = invite.id.toUuid()
            it[roomId] = invite.roomId.toUuid()
            it[userId] = invite.userId.toUuid()
            it[invitedBy] = invite.invitedBy.toUuid()
            it[createdAt] = invite.createdAt
        }.insertedCount > 0
    }

    override suspend fun findById(inviteId: String): RoomInvite? {
        val id = inviteId.toUuidOrNull() ?: return null
        return db.run {
            RoomInvitesTable.selectAll().where { RoomInvitesTable.id eq id }.singleOrNull()?.toInvite()
        }
    }

    override suspend fun forUser(userId: String): List<RoomInvite> = db.run {
        RoomInvitesTable.selectAll()
            .where { RoomInvitesTable.userId eq userId.toUuid() }
            .orderBy(RoomInvitesTable.createdAt, SortOrder.DESC)
            .map { it.toInvite() }
    }

    override suspend fun invitedTo(roomId: String): Set<String> = db.run {
        RoomInvitesTable.selectAll()
            .where { RoomInvitesTable.roomId eq roomId.toUuid() }
            .map { it[RoomInvitesTable.userId].toString() }
            .toSet()
    }

    override suspend fun delete(inviteId: String) {
        val id = inviteId.toUuidOrNull() ?: return
        db.run { RoomInvitesTable.deleteWhere { RoomInvitesTable.id eq id } }
    }

    override suspend fun deleteFor(roomId: String, userId: String) {
        db.run {
            RoomInvitesTable.deleteWhere {
                (RoomInvitesTable.roomId eq roomId.toUuid()) and (RoomInvitesTable.userId eq userId.toUuid())
            }
        }
    }

    private fun ResultRow.toInvite() = RoomInvite(
        id = this[RoomInvitesTable.id].toString(),
        roomId = this[RoomInvitesTable.roomId].toString(),
        userId = this[RoomInvitesTable.userId].toString(),
        invitedBy = this[RoomInvitesTable.invitedBy].toString(),
        createdAt = this[RoomInvitesTable.createdAt],
    )
}
