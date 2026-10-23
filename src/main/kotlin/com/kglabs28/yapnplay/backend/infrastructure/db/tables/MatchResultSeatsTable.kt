package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V3__match_results_and_xp.sql and V15__match_history.sql. */
object MatchResultSeatsTable : Table("match_result_seats") {
    val matchId = uuid("match_id").references(MatchResultsTable.id)
    val seat = integer("seat")
    val userId = uuid("user_id").nullable()
    val place = integer("place")
    val xpAwarded = integer("xp_awarded")
    /** V15: the seat's name and avatar as they were, and its coins (won, for finishing, staked). */
    val displayName = varchar("display_name", NAME_MAX_LENGTH)
    val avatarId = varchar("avatar_id", AVATAR_MAX_LENGTH)
    val coinsWon = integer("coins_won")
    val completionCoins = integer("completion_coins")
    val stake = integer("stake")

    override val primaryKey = PrimaryKey(matchId, seat)

    private const val NAME_MAX_LENGTH = 64
    private const val AVATAR_MAX_LENGTH = 40
}
