package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V5__moderation.sql. */
object ReportsTable : Table("reports") {
    val id = uuid("id")
    val contextId = varchar("context_id", CONTEXT_MAX_LENGTH)
    val reporterId = uuid("reporter_id")
    val reportedId = uuid("reported_id")
    val reason = varchar("reason", REASON_MAX_LENGTH)
    val required = integer("required")
    val createdAt = long("created_at")
    val resolvedAt = long("resolved_at").nullable()
    val upheld = bool("upheld").nullable()

    override val primaryKey = PrimaryKey(id)

    private const val CONTEXT_MAX_LENGTH = 64
    private const val REASON_MAX_LENGTH = 32
}
