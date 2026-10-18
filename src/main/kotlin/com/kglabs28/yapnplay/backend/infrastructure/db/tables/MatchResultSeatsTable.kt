package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V3__match_results_and_xp.sql. */
object MatchResultSeatsTable : Table("match_result_seats") {
    val matchId = uuid("match_id").references(MatchResultsTable.id)
    val seat = integer("seat")
    val userId = uuid("user_id").nullable()
    val place = integer("place")
    val xpAwarded = integer("xp_awarded")

    override val primaryKey = PrimaryKey(matchId, seat)
}
