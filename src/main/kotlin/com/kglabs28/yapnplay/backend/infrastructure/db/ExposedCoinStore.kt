package com.kglabs28.yapnplay.backend.infrastructure.db

import org.jetbrains.exposed.sql.and
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.CoinLedgerTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [CoinStore] on PostgreSQL: each change locks the user's row, so balances never race. */
class ExposedCoinStore(private val db: DbQuery) : CoinStore {

    override suspend fun balance(userId: String): Int {
        val uuid = userId.toUuidOrNull() ?: return 0
        return db.run { UsersTable.selectAll().where { UsersTable.id eq uuid }.singleOrNull()?.get(UsersTable.coins) ?: 0 }
    }

    override suspend fun change(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long): Int? {
        val uuid = userId.toUuidOrNull() ?: return null
        return db.run {
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull() ?: return@run null
            val next = row[UsersTable.coins] + delta
            if (next < 0) return@run null
            UsersTable.update({ UsersTable.id eq uuid }) { it[coins] = next }
            log(userId, delta, reason, matchId, at)
            next
        }
    }

    override suspend fun claimDaily(userId: String, day: Int, amount: Int, at: Long): Boolean {
        val uuid = userId.toUuidOrNull() ?: return false
        return db.run {
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull() ?: return@run false
            if (row[UsersTable.dailyBonusDay] >= day) return@run false
            UsersTable.update({ UsersTable.id eq uuid }) {
                it[coins] = row[UsersTable.coins] + amount
                it[dailyBonusDay] = day
            }
            log(userId, amount, CoinReason.DAILY, null, at)
            true
        }
    }

    override suspend fun claimAdReward(userId: String, day: Int, amount: Int, maxPerDay: Int, at: Long): Int? {
        val uuid = userId.toUuidOrNull() ?: return null
        return db.run {
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull() ?: return@run null
            val usedToday = if (row[UsersTable.adRewardDay] == day) row[UsersTable.adRewardsToday] else 0
            if (usedToday >= maxPerDay) return@run null
            UsersTable.update({ UsersTable.id eq uuid }) {
                it[coins] = row[UsersTable.coins] + amount
                it[adRewardDay] = day
                it[adRewardsToday] = usedToday + 1
            }
            log(userId, amount, CoinReason.AD, null, at)
            maxPerDay - usedToday - 1
        }
    }

    override suspend fun entriesFor(matchId: String): Map<String, Int> {
        val uuid = matchId.toUuidOrNull() ?: return emptyMap()
        return db.run {
            CoinLedgerTable.selectAll()
                .where { (CoinLedgerTable.matchId eq uuid) and (CoinLedgerTable.reason eq CoinReason.ENTRY.name) }
                .associate { it[CoinLedgerTable.userId].toString() to -it[CoinLedgerTable.delta] }
        }
    }

    private fun Transaction.log(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long) {
        CoinLedgerTable.insert {
            it[CoinLedgerTable.userId] = userId.toUuid()
            it[CoinLedgerTable.delta] = delta
            it[CoinLedgerTable.reason] = reason.name
            it[CoinLedgerTable.matchId] = matchId?.toUuidOrNull()
            it[createdAt] = at
        }
    }
}
