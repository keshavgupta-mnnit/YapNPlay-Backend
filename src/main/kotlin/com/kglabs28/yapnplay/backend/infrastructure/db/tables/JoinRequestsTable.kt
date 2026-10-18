package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V4__social.sql. */
object JoinRequestsTable : Table("join_requests") {
    val roomId = uuid("room_id")
    val userId = uuid("user_id").references(UsersTable.id)
    val requestedAt = long("requested_at")

    override val primaryKey = PrimaryKey(roomId, userId)
}
