package com.kglabs28.yapnplay.backend.domain.match

/** A finished match as stored, for a player's history: every seat with its place, coins and XP. */
data class PlayedMatch(
    val matchId: String,
    val gameId: String,
    val roomMatch: Boolean,
    val startedAt: Long,
    val endedAt: Long,
    val seats: List<FinishedSeat>,
)
