package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import com.kglabs28.yapnplay.backend.config.BackendConstants
import org.jetbrains.exposed.sql.Table

/** Mirrors V2__create_play_rooms.sql. */
object PlayRoomsTable : Table("play_rooms") {
    val id = uuid("id")
    val code = char("code", BackendConstants.ROOM_CODE_LENGTH).uniqueIndex()
    val name = varchar("name", BackendConstants.ROOM_NAME_MAX_LENGTH)
    val description = varchar("description", DESCRIPTION_MAX_LENGTH).nullable()
    val icon = varchar("icon", ICON_MAX_LENGTH)
    val createdBy = uuid("created_by").references(UsersTable.id)
    val leaderId = uuid("leader_id")
    val adminId = uuid("admin_id")
    val selectedGame = varchar("selected_game", GAME_ID_MAX_LENGTH).nullable()
    val botFill = bool("bot_fill")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    private const val DESCRIPTION_MAX_LENGTH = 80
    private const val ICON_MAX_LENGTH = 16
    private const val GAME_ID_MAX_LENGTH = 40
}
