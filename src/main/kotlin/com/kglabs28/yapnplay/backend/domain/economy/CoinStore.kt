package com.kglabs28.yapnplay.backend.domain.economy

/** Why coins moved (stored in the ledger). */
enum class CoinReason { DAILY, AD, ENTRY, WIN, COMPLETION, ADMIN }

/** An ad reward claim: [granted] is 0 when this ad id was already paid. */
data class AdClaim(val granted: Int, val adsLeftToday: Int)

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
    suspend fun claimAdReward(userId: String, adId: String, day: Int, amount: Int, maxPerDay: Int, at: Long): AdClaim?

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
