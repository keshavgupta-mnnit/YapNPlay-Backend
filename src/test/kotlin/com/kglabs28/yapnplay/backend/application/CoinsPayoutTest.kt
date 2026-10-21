package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.domain.economy.AdClaimResult
import com.kglabs28.yapnplay.backend.domain.economy.AdRefusal
import com.kglabs28.yapnplay.backend.domain.economy.AdUsage
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The pot is shared by place (CPUs, players who did not pay and leavers get nothing); finishers get completion coins. */
class CoinsPayoutTest {
    private val paid = mutableMapOf<String, Int>()
    private var completionsLeftToday = 10
    private val store = object : CoinStore {
        override suspend fun balance(userId: String) = 0
        override suspend fun change(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long): Int? = 0
        override suspend fun claimDaily(userId: String, day: Int, amount: Int, at: Long) = false
        override suspend fun claimAdReward(userId: String, adId: String, day: Int, amount: Int, maxPerDay: Int, cooldownMillis: Long, at: Long): AdClaimResult =
            AdClaimResult.Refused(AdRefusal.DAILY_LIMIT, AdUsage(0, 0))
        override suspend fun adUsage(userId: String, day: Int) = AdUsage(0, 0)
        override suspend fun entriesFor(matchId: String) = emptyMap<String, Int>()
        override suspend fun settle(
            matchId: String, wins: Map<String, Int>, finishers: List<String>, completionCoins: Int, day: Int, dailyCap: Int, at: Long,
        ): Map<String, Int> {
            wins.forEach { (user, coins) -> paid[user] = (paid[user] ?: 0) + coins }
            return finishers.filter { completionsLeftToday-- > 0 }.associateWith { completionCoins }
        }
    }
    private val coins = CoinsService(store, { 0L })

    private fun match(entry: Int, payers: List<String>, split: List<Int>, vararg seats: FinishedSeat, minutes: Long = 0) =
        FinishedMatch("m", "g", null, 0, minutes * 60_000, seats.toList(), entry, payers, split)

    @Test
    fun `four seats share a 400 pot 50-30-20, the bank paying for the CPU`() = runTest {
        coins.settle(match(100, listOf("a", "c", "d"), listOf(50, 30, 20), FinishedSeat(0, "a", 1), FinishedSeat(1, null, 2), FinishedSeat(2, "c", 3), FinishedSeat(3, "d", 4)))
        assertEquals(mapOf("a" to 200, "c" to 80), paid)
    }

    @Test
    fun `a bigger league wins bigger`() = runTest {
        coins.settle(match(5_000, listOf("a", "b"), listOf(100), FinishedSeat(0, "a", 1), FinishedSeat(1, "b", 2)))
        assertEquals(mapOf("a" to 10_000), paid)
    }

    @Test
    fun `no fee no prize, leavers get nothing, free matches pay nothing`() = runTest {
        // b had no coins so played free; a left; c came last.
        coins.settle(match(100, listOf("a", "c"), listOf(60, 40), FinishedSeat(0, "b", 1), FinishedSeat(1, "a", 2, left = true), FinishedSeat(2, "c", 3)))
        coins.settle(match(0, emptyList(), emptyList(), FinishedSeat(0, "x", 1), FinishedSeat(1, "y", 2)))
        assertEquals(emptyMap(), paid)
    }

    @Test
    fun `the group pot grows with the players`() = runTest {
        val players = (1..6).map { "p$it" }
        coins.settle(match(100, players, listOf(35, 25, 20, 12, 8), *(1..6).map { FinishedSeat(it, "p$it", it) }.toTypedArray()))
        assertEquals(mapOf("p1" to 210, "p2" to 150, "p3" to 120, "p4" to 72, "p5" to 48), paid)
    }

    @Test
    fun `finishers get completion coins, even in free games, but short matches and non-finishers do not`() = runTest {
        val seats = arrayOf(
            FinishedSeat(1, "won", 1, completed = true),
            FinishedSeat(2, "lost", 3, completed = true),
            FinishedSeat(3, "left", 4, left = true),
            FinishedSeat(4, null, 2),
        )
        val long = coins.settle(match(0, emptyList(), emptyList(), *seats, minutes = 5))
        assertEquals(mapOf("won" to 100, "lost" to 100), long.completion)
        val short = coins.settle(match(0, emptyList(), emptyList(), *seats, minutes = 1))
        assertEquals(emptyMap(), short.completion)
    }
}
