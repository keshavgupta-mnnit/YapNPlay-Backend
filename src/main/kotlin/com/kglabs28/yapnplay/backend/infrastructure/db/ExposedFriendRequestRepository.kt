package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.social.FriendRequestRecord
import com.kglabs28.yapnplay.backend.domain.social.FriendRequestRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FriendRequestsTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

/** [FriendRequestRepository] on PostgreSQL. */
class ExposedFriendRequestRepository(private val db: DbQuery) : FriendRequestRepository {

    override suspend fun create(request: FriendRequestRecord) {
        db.run {
            FriendRequestsTable.insertIgnore {
                it[id] = request.id.toUuid()
                it[fromId] = request.fromId.toUuid()
                it[toId] = request.toId.toUuid()
                it[createdAt] = request.createdAt
            }
        }
    }

    override suspend fun find(id: String): FriendRequestRecord? {
        val uuid = id.toUuidOrNull() ?: return null
        return db.run { FriendRequestsTable.selectAll().where { FriendRequestsTable.id eq uuid }.singleOrNull()?.toRecord() }
    }

    override suspend fun incoming(userId: String): List<FriendRequestRecord> = db.run {
        FriendRequestsTable.selectAll().where { FriendRequestsTable.toId eq userId.toUuid() }
            .orderBy(FriendRequestsTable.createdAt, SortOrder.DESC)
            .map { it.toRecord() }
    }

    override suspend fun between(fromId: String, toId: String): FriendRequestRecord? = db.run {
        FriendRequestsTable.selectAll()
            .where { (FriendRequestsTable.fromId eq fromId.toUuid()) and (FriendRequestsTable.toId eq toId.toUuid()) }
            .singleOrNull()?.toRecord()
    }

    override suspend fun delete(id: String) {
        db.run { FriendRequestsTable.deleteWhere { FriendRequestsTable.id eq id.toUuid() } }
    }

    override suspend fun deleteBetween(a: String, b: String) {
        db.run {
            FriendRequestsTable.deleteWhere {
                ((FriendRequestsTable.fromId eq a.toUuid()) and (FriendRequestsTable.toId eq b.toUuid())) or
                    ((FriendRequestsTable.fromId eq b.toUuid()) and (FriendRequestsTable.toId eq a.toUuid()))
            }
        }
    }

    private fun ResultRow.toRecord() = FriendRequestRecord(
        id = this[FriendRequestsTable.id].toString(),
        fromId = this[FriendRequestsTable.fromId].toString(),
        toId = this[FriendRequestsTable.toId].toString(),
        createdAt = this[FriendRequestsTable.createdAt],
    )
}
