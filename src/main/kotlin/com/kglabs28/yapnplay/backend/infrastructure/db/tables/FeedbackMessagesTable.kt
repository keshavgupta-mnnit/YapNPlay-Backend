package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V19__feedback.sql. */
object FeedbackMessagesTable : Table("feedback_messages") {
    val id = uuid("id")
    val ticketId = uuid("ticket_id").references(FeedbackTicketsTable.id)
    val fromAdmin = bool("from_admin")
    val author = varchar("author", AUTHOR_MAX_LENGTH)
    val body = text("body")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)

    private const val AUTHOR_MAX_LENGTH = 64
}
