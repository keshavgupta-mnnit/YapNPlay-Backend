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
    /** V12: the ad view id for AD rows (one payment per id). */
    val ref = varchar("ref", REF_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)

    private const val REASON_MAX_LENGTH = 16
    const val REF_MAX_LENGTH = 64
}
