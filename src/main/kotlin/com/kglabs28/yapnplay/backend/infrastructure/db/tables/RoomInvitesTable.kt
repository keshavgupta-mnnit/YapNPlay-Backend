package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V9__room_invites.sql. */
object RoomInvitesTable : Table("room_invites") {
    val id = uuid("id")
    val roomId = uuid("room_id").references(PlayRoomsTable.id)
    val userId = uuid("user_id").references(UsersTable.id)
    val invitedBy = uuid("invited_by").references(UsersTable.id)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
