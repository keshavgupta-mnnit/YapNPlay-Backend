package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V29__web_crash_reports.sql: the web crash issues an admin closed. */
object WebIssuesTable : Table("web_issues") {
    val fingerprint = varchar("fingerprint", 64)
    val closedAt = long("closed_at").default(0L)
    val closedBy = varchar("closed_by", 80).default("")
    val note = varchar("note", 500).default("")
    override val primaryKey = PrimaryKey(fingerprint)
}
