package com.kglabs28.yapnplay.backend.domain.economy

/** Why coins moved (stored in the ledger). */
enum class CoinReason { DAILY, AD, ENTRY, WIN, COMPLETION, ADMIN }

/** An ad reward claim: [granted] is 0 when this ad id was already paid. */
data class AdClaim(val granted: Int, val adsLeftToday: Int, val lastAdAt: Long)

/** How a player has used rewarded ads: [usedToday] on the day asked about, and when the last one paid. */
data class AdUsage(val usedToday: Int, val lastAdAt: Long)

/** Why an ad reward was refused. */
enum class AdRefusal { DAILY_LIMIT, COOLDOWN }

/** Players' coins and the ledger of every change. */
interface CoinStore {
    suspend fun balance(userId: String): Int

    /**
     * Adds [delta] (negative to take) and records it. Taking more than the
     * balance fails: null. Otherwise the new balance.
     */
    suspend fun change(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long): Int?

    /** Grants [amount] once for [day]; false when today's bonus was already given. */
    suspend fun claimDaily(userId: String, day: Int, amount: Int, at: Long): Boolean

    /**
     * Grants [amount] for the ad view [adId], at most [maxPerDay] times on
     * [day]. A repeated [adId] grants nothing. Null when today's cap is used up.
     */
    /** Pays the day's next step of [rewards] (the Nth ad today pays rewards[N-1]; past the end, the last). */
    suspend fun claimAdReward(userId: String, adId: String, day: Int, rewards: List<Int>, maxPerDay: Int, cooldownMillis: Long, at: Long): AdClaimResult

    /** Rewarded ads used on [day] and when the last one paid. */
    suspend fun adUsage(userId: String, day: Int): AdUsage

    /**
     * Pays a finished match in one transaction: [wins] from the pot, and
     * [completionCoins] to each of [finishers] still under today's
     * [dailyCap]. Returns the completion coins each finisher got.
     */
    suspend fun settle(
        matchId: String,
        wins: Map<String, Int>,
        finishers: List<String>,
        completionCoins: Int,
        day: Int,
        dailyCap: Int,
        at: Long,
    ): Map<String, Int>

    /** Entry fees paid for [matchId]: user id to coins paid. */
    suspend fun entriesFor(matchId: String): Map<String, Int>
}

/** A paid (or replayed) claim, or why it was refused. */
sealed interface AdClaimResult {
    data class Paid(val claim: AdClaim) : AdClaimResult
    data class Refused(val reason: AdRefusal, val usage: AdUsage) : AdClaimResult
}

/** What the ad at [index] today (0 = first) pays: that ladder step, or the last one past the end. */
fun adRewardFor(rewards: List<Int>, index: Int): Int = rewards.getOrElse(index) { rewards.lastOrNull() ?: 0 }
