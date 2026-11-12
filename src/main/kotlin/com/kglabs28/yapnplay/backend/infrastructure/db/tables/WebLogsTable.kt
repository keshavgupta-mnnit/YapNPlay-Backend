package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V27__web_logs.sql and V28__web_log_stack.sql. */
object WebLogsTable : Table("web_logs") {
    val id = uuid("id")
    val createdAt = long("created_at")
    val kind = varchar("kind", 20)
    val stage = varchar("stage", 80)
    val message = varchar("message", 500)
    val userAgent = varchar("user_agent", 300)
    val device = varchar("device", 200)
    val elapsedMs = long("elapsed_ms")
    val version = varchar("version", 40)
    val stack = text("stack").default("")
    override val primaryKey = PrimaryKey(id)
}
