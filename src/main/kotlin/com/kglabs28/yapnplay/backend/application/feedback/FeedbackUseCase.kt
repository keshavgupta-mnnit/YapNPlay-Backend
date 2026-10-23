package com.kglabs28.yapnplay.backend.application.feedback

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.FeedbackMessageView
import com.kglabs28.yapnplay.backend.contract.FeedbackThread
import com.kglabs28.yapnplay.backend.contract.FeedbackTicketSummary
import com.kglabs28.yapnplay.backend.contract.FeedbackTickets
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackKind
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackMessage
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackRepository
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackStatus
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackTicket

/**
 * A player's feedback and support: start a conversation (a new game idea, a
 * feature, an issue, a bug, feedback), list theirs, and read one with the
 * admins' replies (read-only). At most [DAILY_LIMIT] new ones a day, up to
 * [MAX_LENGTH] characters each.
 */
class FeedbackUseCase(
    private val feedback: FeedbackRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun submit(userId: String, kind: String, message: String): FeedbackTicketSummary {
        val type = runCatching { FeedbackKind.valueOf(kind) }.getOrNull()
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.FEEDBACK_BAD_KIND)
        val text = message.trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.FEEDBACK_BAD_LENGTH)
        val now = clock.nowMillis()
        if (feedback.countSince(userId, now - DAY_MILLIS) >= DAILY_LIMIT) {
            throw ServiceException(ErrorCode.RATE_LIMITED, BackendStrings.FEEDBACK_TOO_MANY)
        }
        val ticket = FeedbackTicket(ids.newId(), userId, type, FeedbackStatus.OPEN, now, now, text)
        feedback.create(ticket, FeedbackMessage(ids.newId(), fromAdmin = false, author = "", body = text, createdAt = now))
        return summary(ticket)
    }

    suspend fun mine(userId: String): FeedbackTickets = FeedbackTickets(feedback.byUser(userId, LIST_LIMIT).map(::summary))

    /** Only its own player may read a conversation. */
    suspend fun thread(userId: String, ticketId: String): FeedbackThread {
        val ticket = feedback.find(ticketId)?.takeIf { it.userId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.FEEDBACK_NOT_FOUND)
        return FeedbackThread(summary(ticket), feedback.messages(ticket.id).map { FeedbackMessageView(it.fromAdmin, it.body, it.createdAt) })
    }

    companion object {
        const val MAX_LENGTH = 2_000
        const val DAILY_LIMIT = 10
        private const val LIST_LIMIT = 100
        private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

        fun summary(ticket: FeedbackTicket) = FeedbackTicketSummary(
            ticketId = ticket.id,
            kind = ticket.kind.name,
            answered = ticket.status == FeedbackStatus.ANSWERED,
            preview = ticket.preview,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
        )
    }
}
