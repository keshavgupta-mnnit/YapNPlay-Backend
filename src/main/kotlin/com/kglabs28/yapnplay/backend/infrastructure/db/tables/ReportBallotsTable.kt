package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V5__moderation.sql. */
object ReportBallotsTable : Table("report_ballots") {
    val reportId = uuid("report_id")
    val voterId = uuid("voter_id")
    val response = varchar("response", RESPONSE_MAX_LENGTH)
    val recordedAt = long("recorded_at")

    override val primaryKey = PrimaryKey(reportId, voterId)

    private const val RESPONSE_MAX_LENGTH = 16
}
