package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V6__profiles_friend_requests_blocks.sql. */
object FriendRequestsTable : Table("friend_requests") {
    val id = uuid("id")
    val fromId = uuid("from_id")
    val toId = uuid("to_id")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
