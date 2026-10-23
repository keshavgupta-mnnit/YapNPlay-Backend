package com.kglabs28.yapnplay.backend.domain.feedback

/** Feedback conversations and their messages. */
interface FeedbackRepository {
    /** Starts a conversation with its first message. */
    suspend fun create(ticket: FeedbackTicket, first: FeedbackMessage)

    /** The player's conversations, latest activity first. */
    suspend fun byUser(userId: String, limit: Int): List<FeedbackTicket>

    suspend fun find(ticketId: String): FeedbackTicket?

    /** Oldest first. */
    suspend fun messages(ticketId: String): List<FeedbackMessage>

    /** Adds [message] and sets the conversation's [status] and last activity. */
    suspend fun reply(ticketId: String, message: FeedbackMessage, status: FeedbackStatus)

    /** Every conversation, latest activity first (admin). */
    suspend fun recent(limit: Int): List<FeedbackTicket>

    /** How many conversations [userId] started since [sinceMillis] (the daily limit). */
    suspend fun countSince(userId: String, sinceMillis: Long): Int
}
