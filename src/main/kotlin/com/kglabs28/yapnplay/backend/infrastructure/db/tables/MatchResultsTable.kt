package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V3__match_results_and_xp.sql. */
object MatchResultsTable : Table("match_results") {
    val id = uuid("id")
    val gameId = varchar("game_id", GAME_ID_MAX_LENGTH)
    val roomId = uuid("room_id").nullable()
    val startedAt = long("started_at")
    val endedAt = long("ended_at")

    override val primaryKey = PrimaryKey(id)

    private const val GAME_ID_MAX_LENGTH = 40
}
