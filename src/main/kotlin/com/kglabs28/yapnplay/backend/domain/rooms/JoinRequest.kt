package com.kglabs28.yapnplay.backend.domain.rooms

/** Someone waiting for the admin to let them into a room. */
data class JoinRequest(
    val roomId: String,
    val userId: String,
    val displayName: String,
    val requestedAt: Long,
    val avatarId: String = "",
)
