package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V7__active_matches.sql. */
object ActiveMatchesTable : Table("active_matches") {
    val id = varchar("id", ID_MAX_LENGTH)
    val gameId = varchar("game_id", GAME_ID_MAX_LENGTH)
    val roomId = varchar("room_id", ID_MAX_LENGTH).nullable()
    val version = long("version")
    val updatedAt = long("updated_at")
    val state = text("state")

    override val primaryKey = PrimaryKey(id)

    private const val ID_MAX_LENGTH = 64
    private const val GAME_ID_MAX_LENGTH = 40
}
