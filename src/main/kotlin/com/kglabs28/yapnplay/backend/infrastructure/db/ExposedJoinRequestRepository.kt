package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequest
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequestRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.JoinRequestsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll

/** [JoinRequestRepository] on PostgreSQL. */
class ExposedJoinRequestRepository(private val db: DbQuery) : JoinRequestRepository {

    override suspend fun add(roomId: String, userId: String, at: Long) {
        db.run {
            JoinRequestsTable.insertIgnore {
                it[JoinRequestsTable.roomId] = roomId.toUuid()
                it[JoinRequestsTable.userId] = userId.toUuid()
                it[requestedAt] = at
            }
        }
    }

    override suspend fun remove(roomId: String, userId: String) {
        db.run {
            JoinRequestsTable.deleteWhere {
                (JoinRequestsTable.roomId eq roomId.toUuid()) and (JoinRequestsTable.userId eq userId.toUuid())
            }
        }
    }

    override suspend fun listFor(roomId: String): List<JoinRequest> = db.run {
        JoinRequestsTable.join(UsersTable, JoinType.INNER, JoinRequestsTable.userId, UsersTable.id)
            .selectAll()
            .where { JoinRequestsTable.roomId eq roomId.toUuid() }
            .orderBy(JoinRequestsTable.requestedAt)
            .map { JoinRequest(roomId, it[JoinRequestsTable.userId].toString(), it[UsersTable.displayName], it[JoinRequestsTable.requestedAt]) }
    }
}
