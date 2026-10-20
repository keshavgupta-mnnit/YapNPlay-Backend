package com.kglabs28.yapnplay.backend.application.economy

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.CoinsReward
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.League
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import java.time.Instant
import java.time.ZoneId

/**
 * Coins: play money only (never bought, never cashed out). A daily bonus,
 * rewarded ads, and match pots: every paid seat's entry fee (the bank pays
 * for CPU seats), shared by finishing place. No fee, no prize; players who
 * left get nothing.
 */
class CoinsService(
    private val store: CoinStore,
    private val clock: Clock,
) : EntryFees {

    /** Today's +100, given once a day (India time) the first time the app asks. The coins granted, or 0. */
    suspend fun dailyBonus(userId: String): Int {
        val now = clock.nowMillis()
        return if (store.claimDaily(userId, dayOf(now), BackendConstants.DAILY_BONUS_COINS, now)) BackendConstants.DAILY_BONUS_COINS else 0
    }

    /** A fully watched rewarded ad: +500, a few times a day (ads are not verified yet). */
    suspend fun adReward(userId: String): CoinsReward {
        val now = clock.nowMillis()
        val left = store.claimAdReward(userId, dayOf(now), BackendConstants.AD_REWARD_COINS, BackendConstants.AD_REWARDS_PER_DAY, now)
            ?: throw ServiceException(ErrorCode.RATE_LIMITED, BackendStrings.AD_REWARDS_USED)
        return CoinsReward(coins = store.balance(userId), granted = BackendConstants.AD_REWARD_COINS, adsLeftToday = left)
    }

    suspend fun balance(userId: String): Int = store.balance(userId)

    /** The Quick Play leagues, and which ones this player can afford now. */
    suspend fun leagues(userId: String): Leagues {
        val coins = store.balance(userId)
        return Leagues(coins, BackendConstants.LEAGUES.map { (name, entry) -> League(entry, name, coins >= entry) })
    }

    override suspend fun canEnter(userId: String, entryCoins: Int): Boolean =
        entryCoins == 0 || store.balance(userId) >= entryCoins

    override suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int): List<String> {
        if (entryCoins <= 0) return emptyList()
        val now = clock.nowMillis()
        return userIds.filter { store.change(it, -entryCoins, CoinReason.ENTRY, matchId, now) != null }
    }

    /**
     * The pot (entry × paid seats, CPU seats paid by the bank) shared by
     * place. Players who did not pay or who left get nothing; their share
     * stays with the bank.
     */
    suspend fun payout(match: FinishedMatch): Map<String, Int> {
        val paid = store.entriesFor(match.matchId)
        if (paid.isEmpty()) return emptyMap()
        val entry = paid.values.max()
        val cpus = match.seats.count { it.userId == null }
        val pot = entry * (paid.size + cpus)
        val shares = SHARES[match.seats.size] ?: return emptyMap()
        val now = clock.nowMillis()
        return match.seats.mapNotNull { seat ->
            val userId = seat.userId ?: return@mapNotNull null
            val percent = shares.getOrNull(seat.place - 1) ?: return@mapNotNull null
            if (seat.left || userId !in paid || percent == 0) return@mapNotNull null
            val won = pot * percent / PERCENT
            store.change(userId, won, CoinReason.WIN, match.matchId, now)
            userId to won
        }.toMap()
    }

    private fun dayOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().toEpochDay().toInt()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        const val PERCENT = 100
        /** Pot share (%) by place, per table size; the last place gets nothing. */
        val SHARES = mapOf(
            2 to listOf(100),
            3 to listOf(60, 40),
            4 to listOf(50, 30, 20),
            5 to listOf(40, 30, 20, 10),
            6 to listOf(35, 25, 20, 12, 8),
        )
    }
}

/** Entry fees in coins; the free version lets everyone in and charges nobody. */
interface EntryFees {
    suspend fun canEnter(userId: String, entryCoins: Int): Boolean

    /** Takes [entryCoins] from each player who has it; returns those who paid. */
    suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int): List<String>

    object Free : EntryFees {
        override suspend fun canEnter(userId: String, entryCoins: Int) = true
        override suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int) = emptyList<String>()
    }
}
