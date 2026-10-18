package com.kglabs28.yapnplay.backend.application.match

/** Everything needed to open a match. [houseRules] null: a public match with traditional rules. */
data class MatchSetup(
    val matchId: String,
    val gameId: String,
    val roomId: String?,
    val title: String,
    val players: List<SeatHolder>,
    val cpuCount: Int,
    val houseRules: Map<String, Boolean>?,
)

/** A human taking a seat. */
data class SeatHolder(
    val userId: String,
    val displayName: String,
    val level: Int,
)
