package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V5__moderation.sql. */
object PenaltiesTable : Table("penalties") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val reportId = uuid("report_id").nullable()
    val points = integer("points")
    val blockedUntil = long("blocked_until")
    val createdAt = long("created_at")
    val clearedAt = long("cleared_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
