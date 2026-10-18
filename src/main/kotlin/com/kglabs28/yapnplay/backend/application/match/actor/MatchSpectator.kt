package com.kglabs28.yapnplay.backend.application.match.actor

/** Someone watching a room match, since [since] (epoch millis). */
data class MatchSpectator(
    val userId: String,
    val displayName: String,
    val since: Long,
    val micOn: Boolean = false,
    val level: Int = 1,
    val avatarId: String = "",
)
