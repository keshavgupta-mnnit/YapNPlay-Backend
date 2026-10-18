package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V4__social.sql. */
object DevicesTable : Table("devices") {
    val userId = uuid("user_id")
    val token = varchar("token", TOKEN_MAX_LENGTH)
    val platform = varchar("platform", PLATFORM_MAX_LENGTH)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(userId, token)

    private const val TOKEN_MAX_LENGTH = 512
    private const val PLATFORM_MAX_LENGTH = 16
}
