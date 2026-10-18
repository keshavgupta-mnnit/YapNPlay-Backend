package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V8__match_outcomes.sql. */
object MatchOutcomesTable : Table("match_outcomes") {
    val matchId = varchar("match_id", ID_MAX_LENGTH)
    val gameId = varchar("game_id", GAME_ID_MAX_LENGTH)
    val roomId = varchar("room_id", ID_MAX_LENGTH).nullable()
    val startedAt = long("started_at")
    val endedAt = long("ended_at").nullable()
    val status = varchar("status", STATUS_MAX_LENGTH)
    val reason = text("reason").nullable()
    val errorCount = integer("error_count")

    override val primaryKey = PrimaryKey(matchId)

    private const val ID_MAX_LENGTH = 64
    private const val GAME_ID_MAX_LENGTH = 40
    private const val STATUS_MAX_LENGTH = 20
}
