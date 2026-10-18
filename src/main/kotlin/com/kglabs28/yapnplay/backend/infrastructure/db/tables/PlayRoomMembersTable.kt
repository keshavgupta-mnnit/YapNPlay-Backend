package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V2__create_play_rooms.sql. */
object PlayRoomMembersTable : Table("play_room_members") {
    val roomId = uuid("room_id").references(PlayRoomsTable.id)
    val userId = uuid("user_id").references(UsersTable.id)
    val joinedAt = long("joined_at")

    override val primaryKey = PrimaryKey(roomId, userId)
}
