package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclineTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import kotlin.test.Test
import kotlin.test.assertEquals

/** The bots weigh rent ahead and what each sale really costs, as a person would. */
class CityBazaarSmartBotTest {
    private val engine = CityBazaarEngine()
    private val autoplay = CityBazaarBot(CityBazaarBotProfile.AUTOPLAY)

    private fun start(): CityBazaarState = engine.start(CityBazaarRules.STANDARD, 3, object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }) as CityBazaarState

    /** Seat 1 just landed on Mumbai (₹8,500) with [cash]. */
    private fun offeredMumbai(cash: Int, rivals: Map<Int, Int> = emptyMap(), levels: Map<Int, Int> = emptyMap()): CityBazaarState =
        start().let { s ->
            s.copy(
                phase = CityBazaarPhase.BUY, pendingTile = 1, currentSeat = 1,
                positions = mapOf(1 to 1, 2 to 20, 3 to 20), owners = rivals, levels = levels,
                cash = s.cash + (1 to cash),
            )
        }

    @Test
    fun `with plenty of cash and a quiet board it buys Mumbai`() {
        assertEquals(BuyTile, autoplay.chooseAction(offeredMumbai(13_000), SeatNumber(1)))
    }

    @Test
    fun `with 10,000 and heavy rent just ahead it won't buy an 8,500 city`() {
        val rivals = mapOf(4 to 2, 6 to 2, 8 to 2, 10 to 2)
        val hotels = rivals.keys.associateWith { 4 }
        assertEquals(DeclineTile, autoplay.chooseAction(offeredMumbai(10_000, rivals, hotels), SeatNumber(1)))
        assertEquals(DeclineTile, autoplay.chooseAction(offeredMumbai(13_000, rivals, hotels), SeatNumber(1)))
    }

    @Test
    fun `in debt it sells a cold lone city before the houses of its best set or breaking the set`() {
        // Yellow set (Shimla, Amritsar, Srinagar: rent doubled), Srinagar with 3 houses and the most rent so far; Indore alone and cold.
        val s = start().let { s0 ->
            s0.copy(
                currentSeat = 1, phase = CityBazaarPhase.DEBT,
                owners = mapOf(14 to 1, 15 to 1, 17 to 1, 6 to 1),
                levels = mapOf(17 to 3),
                rentByTile = mapOf(17 to 12_000, 14 to 1_500, 15 to 1_800),
                cash = s0.cash + (1 to 0),
                debt = CityBazaarDebt(700, mapOf(2 to 700), MoneyReason.RENT, 21),
            )
        }
        assertEquals(6 to false, CityBazaarBot.nextSale(s, 1))
    }

    /** Seat 1 in jail deciding whether to pay, with no round limit unless [roundLimit]. */
    private fun jailed(cash: Int, owners: Map<Int, Int> = emptyMap(), levels: Map<Int, Int> = emptyMap(), roundLimit: Int? = null, round: Int = 1) =
        start().let { s ->
            s.copy(
                phase = CityBazaarPhase.JAIL_CHOICE, currentSeat = 1, jailTriesLeft = 3, round = round,
                positions = mapOf(1 to 9, 2 to 30, 3 to 30), owners = owners, levels = levels,
                cash = s.cash + (1 to cash),
                cbRules = s.cbRules.copy(house = s.cbRules.house.copy(roundLimit = roundLimit, timeLimitMinutes = null)),
            )
        }

    @Test
    fun `early, with cities to buy ahead, it pays its way out of jail`() {
        assertEquals(PayJailFine, autoplay.chooseAction(jailed(10_000), SeatNumber(1)))
    }

    @Test
    fun `late, with rivals' hotels ahead, it stays in jail`() {
        val ahead = listOf(10, 11, 14, 15, 17, 19, 21)
        val s = jailed(10_000, ahead.associateWith { 2 }, ahead.associateWith { 4 }, roundLimit = 5, round = 5)
        assertEquals(StayInJail, autoplay.chooseAction(s, SeatNumber(1)))
    }

    @Test
    fun `a strong portfolio with plenty of cash pays the fine rather than take half rent`() {
        // The yellow set with hotels, rivals' hotels ahead too: rent dodged is only postponed.
        val mine = listOf(14, 15, 17).associateWith { 1 }
        val theirs = listOf(10, 11, 19, 21).associateWith { 2 }
        val levels = (mine.keys + theirs.keys).associateWith { 4 }
        assertEquals(PayJailFine, autoplay.chooseAction(jailed(60_000, mine + theirs, levels), SeatNumber(1)))
    }

    @Test
    fun `little cash on a board full of rivals' hotels stays`() {
        val ahead = listOf(10, 11, 14, 15, 17, 19, 21)
        assertEquals(StayInJail, autoplay.chooseAction(jailed(2_000, ahead.associateWith { 2 }, ahead.associateWith { 4 }), SeatNumber(1)))
    }
}
