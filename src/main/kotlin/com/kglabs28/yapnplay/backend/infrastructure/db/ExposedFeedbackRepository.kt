package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackKind
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackMessage
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackRepository
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackStatus
import com.kglabs28.yapnplay.backend.domain.feedback.FeedbackTicket
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FeedbackMessagesTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FeedbackTicketsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class ExposedFeedbackRepository(private val db: DbQuery) : FeedbackRepository {

    override suspend fun create(ticket: FeedbackTicket, first: FeedbackMessage) {
        val ticketId = UUID.fromString(ticket.id)
        db.run {
            FeedbackTicketsTable.insert {
                it[id] = ticketId
                it[userId] = UUID.fromString(ticket.userId)
                it[kind] = ticket.kind.name
                it[status] = ticket.status.name
                it[createdAt] = ticket.createdAt
                it[updatedAt] = ticket.updatedAt
            }
            insertMessage(ticketId, first)
        }
    }

    override suspend fun byUser(userId: String, limit: Int): List<FeedbackTicket> {
        val uuid = userId.toUuidOrNull() ?: return emptyList()
        return db.run {
            val rows = FeedbackTicketsTable.selectAll().where { FeedbackTicketsTable.userId eq uuid }
                .orderBy(FeedbackTicketsTable.updatedAt, SortOrder.DESC).limit(limit).toList()
            withPreviews(rows)
        }
    }

    override suspend fun find(ticketId: String): FeedbackTicket? {
        val uuid = ticketId.toUuidOrNull() ?: return null
        return db.run { withPreviews(FeedbackTicketsTable.selectAll().where { FeedbackTicketsTable.id eq uuid }.toList()).firstOrNull() }
    }

    override suspend fun messages(ticketId: String): List<FeedbackMessage> {
        val uuid = ticketId.toUuidOrNull() ?: return emptyList()
        return db.run {
            FeedbackMessagesTable.selectAll().where { FeedbackMessagesTable.ticketId eq uuid }
                .orderBy(FeedbackMessagesTable.createdAt, SortOrder.ASC).map { it.toMessage() }
        }
    }

    override suspend fun reply(ticketId: String, message: FeedbackMessage, status: FeedbackStatus) {
        val uuid = ticketId.toUuidOrNull() ?: return
        db.run {
            insertMessage(uuid, message)
            FeedbackTicketsTable.update({ FeedbackTicketsTable.id eq uuid }) {
                it[FeedbackTicketsTable.status] = status.name
                it[updatedAt] = message.createdAt
            }
        }
    }

    override suspend fun recent(limit: Int): List<FeedbackTicket> = db.run {
        withPreviews(FeedbackTicketsTable.selectAll().orderBy(FeedbackTicketsTable.updatedAt, SortOrder.DESC).limit(limit).toList())
    }

    override suspend fun countSince(userId: String, sinceMillis: Long): Int {
        val uuid = userId.toUuidOrNull() ?: return 0
        return db.run {
            FeedbackTicketsTable.selectAll()
                .where { (FeedbackTicketsTable.userId eq uuid) and (FeedbackTicketsTable.createdAt greaterEq sinceMillis) }
                .count().toInt()
        }
    }

    private fun Transaction.insertMessage(ticketId: UUID, message: FeedbackMessage) {
        FeedbackMessagesTable.insert {
            it[id] = UUID.fromString(message.id)
            it[FeedbackMessagesTable.ticketId] = ticketId
            it[fromAdmin] = message.fromAdmin
            it[author] = message.author.take(AUTHOR_MAX)
            it[body] = message.body
            it[createdAt] = message.createdAt
        }
    }

    /** The tickets in [rows] with each one's first message as its preview and its player's name. */
    private fun Transaction.withPreviews(rows: List<ResultRow>): List<FeedbackTicket> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it[FeedbackTicketsTable.id] }
        val firsts = FeedbackMessagesTable.selectAll().where { FeedbackMessagesTable.ticketId inList ids }
            .orderBy(FeedbackMessagesTable.createdAt, SortOrder.ASC).toList()
            .groupBy { it[FeedbackMessagesTable.ticketId] }.mapValues { it.value.first()[FeedbackMessagesTable.body] }
        val users = rows.map { it[FeedbackTicketsTable.userId] }.distinct()
        val people = UsersTable.selectAll().where { UsersTable.id inList users }
            .associate { it[UsersTable.id] to (it[UsersTable.displayName] to it[UsersTable.userCode]) }
        return rows.map { row ->
            val person = people[row[FeedbackTicketsTable.userId]]
            FeedbackTicket(
                id = row[FeedbackTicketsTable.id].toString(),
                userId = row[FeedbackTicketsTable.userId].toString(),
                kind = runCatching { FeedbackKind.valueOf(row[FeedbackTicketsTable.kind]) }.getOrDefault(FeedbackKind.FEEDBACK),
                status = runCatching { FeedbackStatus.valueOf(row[FeedbackTicketsTable.status]) }.getOrDefault(FeedbackStatus.OPEN),
                createdAt = row[FeedbackTicketsTable.createdAt],
                updatedAt = row[FeedbackTicketsTable.updatedAt],
                preview = firsts[row[FeedbackTicketsTable.id]].orEmpty(),
                userName = person?.first.orEmpty(),
                userCode = person?.second,
            )
        }
    }

    private fun ResultRow.toMessage() = FeedbackMessage(
        id = this[FeedbackMessagesTable.id].toString(),
        fromAdmin = this[FeedbackMessagesTable.fromAdmin],
        author = this[FeedbackMessagesTable.author],
        body = this[FeedbackMessagesTable.body],
        createdAt = this[FeedbackMessagesTable.createdAt],
    )

    private companion object {
        const val AUTHOR_MAX = 64
    }
}
