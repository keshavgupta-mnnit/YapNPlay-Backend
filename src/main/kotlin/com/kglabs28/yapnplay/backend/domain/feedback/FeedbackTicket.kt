package com.kglabs28.yapnplay.backend.domain.feedback

/** One conversation: what it's about, its state, and its first message as a preview. */
data class FeedbackTicket(
    val id: String,
    val userId: String,
    val kind: FeedbackKind,
    val status: FeedbackStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val preview: String,
    /** The player's name (admin list). */
    val userName: String = "",
    val userCode: String? = null,
)

/** One message in a conversation: the player's, or an admin's reply ([author] is the admin's ID). */
data class FeedbackMessage(
    val id: String,
    val fromAdmin: Boolean,
    val author: String,
    val body: String,
    val createdAt: Long,
)
