package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.feedback.FeedbackUseCase
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackMessage
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackRepository
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackStatus
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackTicket
import kotlinx.serialization.Serializable

/** The dashboard's Feedback tab: every conversation, one with its messages, and an admin's reply (the player sees it in the app). */
class AdminFeedbackUseCase(
    private val feedback: FeedbackRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun list(): List<AdminFeedbackView> = feedback.recent(LIST_LIMIT).map(::view)

    suspend fun thread(ticketId: String): AdminFeedbackThread {
        val ticket = feedback.find(ticketId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.FEEDBACK_NOT_FOUND)
        return AdminFeedbackThread(view(ticket), feedback.messages(ticket.id).map { AdminFeedbackMessage(it.fromAdmin, it.author, it.body, it.createdAt) })
    }

    suspend fun reply(ticketId: String, adminId: String, message: String): AdminFeedbackThread {
        val text = message.trim()
        if (text.isEmpty() || text.length > FeedbackUseCase.MAX_LENGTH) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.FEEDBACK_BAD_LENGTH)
        feedback.find(ticketId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.FEEDBACK_NOT_FOUND)
        feedback.reply(ticketId, FeedbackMessage(ids.newId(), fromAdmin = true, author = adminId, body = text, createdAt = clock.nowMillis()), FeedbackStatus.ANSWERED)
        return thread(ticketId)
    }

    private fun view(ticket: FeedbackTicket) = AdminFeedbackView(
        ticket.id, ticket.userId, ticket.userName, ticket.userCode, ticket.kind.name, ticket.status.name, ticket.preview, ticket.createdAt, ticket.updatedAt,
    )

    private companion object {
        const val LIST_LIMIT = 200
    }
}

@Serializable
data class AdminFeedbackView(
    val ticketId: String,
    val userId: String,
    val userName: String,
    val userCode: String?,
    val kind: String,
    val status: String,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AdminFeedbackMessage(val fromAdmin: Boolean, val author: String, val body: String, val createdAt: Long)

@Serializable
data class AdminFeedbackThread(val ticket: AdminFeedbackView, val messages: List<AdminFeedbackMessage>)

@Serializable
data class AdminFeedbackReplyBody(val message: String)
