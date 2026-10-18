package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import com.kglabs28.yapnplay.backend.config.BackendConstants
import org.jetbrains.exposed.sql.Table

/** Mirrors V1__create_users.sql. */
object UsersTable : Table("users") {
    val id = uuid("id")
    val displayName = varchar("display_name", BackendConstants.DISPLAY_NAME_MAX_LENGTH)
    val secretHash = char("secret_hash", SHA256_HEX_LENGTH)
    val createdAt = long("created_at")
    val xp = integer("xp")
    val username = varchar("username", USERNAME_MAX_LENGTH)
    val userCode = char("user_code", USER_CODE_LENGTH).nullable()
    val avatarId = varchar("avatar_id", AVATAR_ID_MAX_LENGTH)

    override val primaryKey = PrimaryKey(id)

    private const val SHA256_HEX_LENGTH = 64
    private const val USERNAME_MAX_LENGTH = 20
    private const val USER_CODE_LENGTH = 6
    private const val AVATAR_ID_MAX_LENGTH = 8
}
