package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V19__feedback.sql. */
object FeedbackTicketsTable : Table("feedback_tickets") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UsersTable.id)
    val kind = varchar("kind", KIND_MAX_LENGTH)
    val status = varchar("status", STATUS_MAX_LENGTH)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    override val primaryKey = PrimaryKey(id)

    private const val KIND_MAX_LENGTH = 30
    private const val STATUS_MAX_LENGTH = 20
}
