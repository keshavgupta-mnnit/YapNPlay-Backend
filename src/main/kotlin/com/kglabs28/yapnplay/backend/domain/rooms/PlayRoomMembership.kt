package com.kglabs28.yapnplay.backend.domain.rooms

/** A stored membership, with the member's current display name. */
data class PlayRoomMembership(
    val roomId: String,
    val userId: String,
    val displayName: String,
    val joinedAt: Long,
    val avatarId: String = "",
)
