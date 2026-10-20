package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Quick Play pot: shared by place; CPUs and leavers get nothing; rooms pay nothing. */
class CoinsPayoutTest {
    private val paid = mutableMapOf<String, Int>()
    private val entries = mutableMapOf<String, Int>()
    private val store = object : CoinStore {
        override suspend fun balance(userId: String) = 0
        override suspend fun change(userId: String, delta: Int, reason: CoinReason, matchId: String?, at: Long): Int? {
            paid[userId] = (paid[userId] ?: 0) + delta
            return 0
        }
        override suspend fun claimDaily(userId: String, day: Int, amount: Int, at: Long) = false
        override suspend fun claimAdReward(userId: String, day: Int, amount: Int, maxPerDay: Int, at: Long): Int? = null
        override suspend fun entriesFor(matchId: String) = entries.toMap()
    }
    private val coins = CoinsService(store) { 0L }

    private fun match(roomId: String?, vararg seats: FinishedSeat) = FinishedMatch("m", "g", roomId, 0, 1, seats.toList())

    @Test
    fun `four seats share a 400 pot 50-30-20, the bank paying for the CPU`() = runTest {
        entries += mapOf("a" to 100, "c" to 100, "d" to 100)
        coins.payout(match(null, FinishedSeat(0, "a", 1), FinishedSeat(1, null, 2), FinishedSeat(2, "c", 3), FinishedSeat(3, "d", 4)))
        assertEquals(mapOf("a" to 200, "c" to 80), paid)
    }

    @Test
    fun `a bigger league wins bigger`() = runTest {
        entries += mapOf("a" to 5_000, "b" to 5_000)
        coins.payout(match(null, FinishedSeat(0, "a", 1), FinishedSeat(1, "b", 2)))
        assertEquals(mapOf("a" to 10_000), paid)
    }

    @Test
    fun `no fee no prize, leavers get nothing, starter league pays nothing`() = runTest {
        // Group game: b had no coins so played free; the pot is only a's and c's fees.
        entries += mapOf("a" to 100, "c" to 100)
        coins.payout(match("room", FinishedSeat(0, "b", 1), FinishedSeat(1, "a", 2, left = true), FinishedSeat(2, "c", 3)))
        assertEquals(emptyMap(), paid)
        entries.clear()
        coins.payout(match(null, FinishedSeat(0, "x", 1), FinishedSeat(1, "y", 2)))
        assertEquals(emptyMap(), paid)
    }

    @Test
    fun `the group pot grows with the players`() = runTest {
        entries += (1..6).associate { "p$it" to 100 }
        coins.payout(match("room", *(1..6).map { FinishedSeat(it, "p$it", it) }.toTypedArray()))
        assertEquals(mapOf("p1" to 210, "p2" to 150, "p3" to 120, "p4" to 72, "p5" to 48), paid)
    }
}
