package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V4__social.sql. */
object FriendshipsTable : Table("friendships") {
    val userId = uuid("user_id")
    val friendId = uuid("friend_id")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(userId, friendId)
}
