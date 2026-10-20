package com.kglabs28.yapnplay.backend.infrastructure.db.tables

import org.jetbrains.exposed.sql.Table

/** Mirrors V11__coins.sql. */
object CoinLedgerTable : Table("coin_ledger") {
    val id = long("id").autoIncrement()
    val userId = uuid("user_id").references(UsersTable.id)
    val delta = integer("delta")
    val reason = varchar("reason", REASON_MAX_LENGTH)
    val matchId = uuid("match_id").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    private const val REASON_MAX_LENGTH = 16
}
