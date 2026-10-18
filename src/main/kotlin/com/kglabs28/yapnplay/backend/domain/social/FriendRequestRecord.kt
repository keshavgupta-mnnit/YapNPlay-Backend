package com.kglabs28.yapnplay.backend.domain.social

/** [fromId] asked [toId] to be friends at [createdAt]. */
data class FriendRequestRecord(
    val id: String,
    val fromId: String,
    val toId: String,
    val createdAt: Long,
)
