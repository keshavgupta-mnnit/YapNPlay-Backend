package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.Serializable

/** `POST /feedback`: a new conversation. [kind] is NEW_GAME, NEW_FEATURE, ISSUE, BUG or FEEDBACK. */
@Serializable
data class SubmitFeedbackBody(
    val kind: String,
    val message: String,
)

/** One conversation in the player's list. */
@Serializable
data class FeedbackTicketSummary(
    val ticketId: String,
    val kind: String,
    /** True once an admin has replied (since the player's last message). */
    val answered: Boolean,
    /** The first message, as a preview. */
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** `GET /feedback`: the player's conversations, latest activity first. */
@Serializable
data class FeedbackTickets(
    val tickets: List<FeedbackTicketSummary>,
)

/** One message in a conversation. */
@Serializable
data class FeedbackMessageView(
    val fromAdmin: Boolean,
    val body: String,
    val createdAt: Long,
)

/** `GET /feedback/{ticketId}`: a conversation with its messages, oldest first (read-only in the app). */
@Serializable
data class FeedbackThread(
    val ticket: FeedbackTicketSummary,
    val messages: List<FeedbackMessageView>,
)
