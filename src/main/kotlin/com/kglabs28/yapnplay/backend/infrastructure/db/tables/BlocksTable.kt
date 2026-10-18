package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V6__profiles_friend_requests_blocks.sql. */
object BlocksTable : Table("blocks") {
    val blockerId = uuid("blocker_id")
    val blockedId = uuid("blocked_id")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(blockerId, blockedId)
}
