package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.social.FriendRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FriendshipsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

/** [FriendRepository] on PostgreSQL: one row each way. */
class ExposedFriendRepository(private val db: DbQuery) : FriendRepository {

    override suspend fun addMutual(a: String, b: String, at: Long) {
        db.run {
            listOf(a to b, b to a).forEach { (user, friend) ->
                FriendshipsTable.insertIgnore {
                    it[userId] = user.toUuid()
                    it[friendId] = friend.toUuid()
                    it[createdAt] = at
                }
            }
        }
    }

    override suspend fun removeMutual(a: String, b: String) {
        db.run {
            FriendshipsTable.deleteWhere {
                ((FriendshipsTable.userId eq a.toUuid()) and (FriendshipsTable.friendId eq b.toUuid())) or
                    ((FriendshipsTable.userId eq b.toUuid()) and (FriendshipsTable.friendId eq a.toUuid()))
            }
        }
    }

    override suspend fun friendsOf(userId: String): Set<String> = db.run {
        FriendshipsTable.selectAll().where { FriendshipsTable.userId eq userId.toUuid() }
            .map { it[FriendshipsTable.friendId].toString() }
            .toSet()
    }
}
