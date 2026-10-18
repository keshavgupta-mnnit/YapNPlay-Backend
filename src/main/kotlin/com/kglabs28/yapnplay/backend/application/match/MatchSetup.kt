package com.kglabs28.yapnplay.backend.application.match

import kotlinx.serialization.Serializable

/** Everything needed to open a match. [houseRules] null: a public match with traditional rules. */
@Serializable
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
@Serializable
data class SeatHolder(
    val userId: String,
    val displayName: String,
    val level: Int,
    val avatarId: String = "",
)
