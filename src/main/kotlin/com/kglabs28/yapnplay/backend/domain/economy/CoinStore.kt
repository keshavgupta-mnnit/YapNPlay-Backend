package com.kglabs28.yapnplay.backend.domain.economy

/** Why coins moved (stored in the ledger). */
enum class CoinReason { DAILY, AD, ENTRY, WIN }

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

    /** Grants [amount] for an ad, at most [maxPerDay] times on [day]; the ads left today, or null when capped. */
    suspend fun claimAdReward(userId: String, day: Int, amount: Int, maxPerDay: Int, at: Long): Int?

    /** Entry fees paid for [matchId]: user id to coins paid. */
    suspend fun entriesFor(matchId: String): Map<String, Int>
}
