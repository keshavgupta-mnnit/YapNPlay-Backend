package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.economy.AdUsage
import com.kglabs28.yapnplay.backend.domain.economy.AdRefusal
import com.kglabs28.yapnplay.backend.domain.economy.AdClaimResult
import com.kglabs28.yapnplay.backend.domain.economy.AdClaim
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

    override suspend fun claimAdReward(
        userId: String,
        adId: String,
        day: Int,
        amount: Int,
        maxPerDay: Int,
        cooldownMillis: Long,
        at: Long,
    ): AdClaimResult {
        val uuid = userId.toUuidOrNull() ?: return AdClaimResult.Refused(AdRefusal.DAILY_LIMIT, AdUsage(0, 0))
        return db.run {
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull()
                ?: return@run AdClaimResult.Refused(AdRefusal.DAILY_LIMIT, AdUsage(0, 0))
            val usedToday = if (row[UsersTable.adRewardDay] == day) row[UsersTable.adRewardsToday] else 0
            val lastAdAt = row[UsersTable.lastAdAt]
            // The same ad view sent again (retry or replay): nothing more, the same answer.
            val seen = CoinLedgerTable.selectAll().where { (CoinLedgerTable.userId eq uuid) and (CoinLedgerTable.ref eq adId) }.any()
            if (seen) return@run AdClaimResult.Paid(AdClaim(0, (maxPerDay - usedToday).coerceAtLeast(0), lastAdAt))
            if (usedToday >= maxPerDay) return@run AdClaimResult.Refused(AdRefusal.DAILY_LIMIT, AdUsage(usedToday, lastAdAt))
            if (lastAdAt > 0 && at - lastAdAt < cooldownMillis) return@run AdClaimResult.Refused(AdRefusal.COOLDOWN, AdUsage(usedToday, lastAdAt))
            UsersTable.update({ UsersTable.id eq uuid }) {
                it[coins] = row[UsersTable.coins] + amount
                it[adRewardDay] = day
                it[adRewardsToday] = usedToday + 1
                it[UsersTable.lastAdAt] = at
            }
            log(userId, amount, CoinReason.AD, null, at, adId)
            AdClaimResult.Paid(AdClaim(amount, maxPerDay - usedToday - 1, at))
        }
    }

    override suspend fun adUsage(userId: String, day: Int): AdUsage {
        val uuid = userId.toUuidOrNull() ?: return AdUsage(0, 0)
        return db.run {
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.singleOrNull() ?: return@run AdUsage(0, 0)
            AdUsage(if (row[UsersTable.adRewardDay] == day) row[UsersTable.adRewardsToday] else 0, row[UsersTable.lastAdAt])
        }
    }

    override suspend fun settle(
        matchId: String,
        wins: Map<String, Int>,
        finishers: List<String>,
        completionCoins: Int,
        day: Int,
        dailyCap: Int,
        at: Long,
    ): Map<String, Int> = db.run {
        wins.forEach { (userId, coins) ->
            val uuid = userId.toUuidOrNull() ?: return@forEach
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull() ?: return@forEach
            UsersTable.update({ UsersTable.id eq uuid }) { it[UsersTable.coins] = row[UsersTable.coins] + coins }
            log(userId, coins, CoinReason.WIN, matchId, at)
        }
        if (completionCoins <= 0) return@run emptyMap()
        finishers.mapNotNull { userId ->
            val uuid = userId.toUuidOrNull() ?: return@mapNotNull null
            val row = UsersTable.selectAll().where { UsersTable.id eq uuid }.forUpdate().singleOrNull() ?: return@mapNotNull null
            val doneToday = if (row[UsersTable.completionDay] == day) row[UsersTable.completionsToday] else 0
            if (doneToday >= dailyCap) return@mapNotNull null
            UsersTable.update({ UsersTable.id eq uuid }) {
                it[UsersTable.coins] = row[UsersTable.coins] + completionCoins
                it[completionDay] = day
                it[completionsToday] = doneToday + 1
            }
            log(userId, completionCoins, CoinReason.COMPLETION, matchId, at)
            userId to completionCoins
        }.toMap()
    }

    override suspend fun entriesFor(matchId: String): Map<String, Int> {
        val uuid = matchId.toUuidOrNull() ?: return emptyMap()
        return db.run {
            CoinLedgerTable.selectAll()
                .where { (CoinLedgerTable.matchId eq uuid) and (CoinLedgerTable.reason eq CoinReason.ENTRY.name) }
                .associate { it[CoinLedgerTable.userId].toString() to -it[CoinLedgerTable.delta] }
        }
    }

    private fun Transaction.log(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long, ref: String? = null) {
        CoinLedgerTable.insert {
            it[CoinLedgerTable.userId] = userId.toUuid()
            it[CoinLedgerTable.delta] = delta
            it[CoinLedgerTable.reason] = reason.name
            it[CoinLedgerTable.matchId] = matchId?.toUuidOrNull()
            it[createdAt] = at
            it[CoinLedgerTable.ref] = ref
        }
    }
}
