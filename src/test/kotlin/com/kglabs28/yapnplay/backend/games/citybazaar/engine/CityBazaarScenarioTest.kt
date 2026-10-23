package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuildOnTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CLUB_TILE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.REST_HOUSE_TILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bot sees what a person sees: every bill ahead, what a rival gains, and who is about to go broke. */
class CityBazaarScenarioTest {
    private val engine = CityBazaarEngine()
    private val bot = CityBazaarBot(CityBazaarBotProfile.AUTOPLAY)

    private fun start(): CityBazaarState = engine.start(CityBazaarRules.STANDARD, 3, object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }) as CityBazaarState

    @Test
    fun `club and rest house and the wealth tax count as bills`() {
        val s = start().copy(owners = mapOf(14 to 1), levels = mapOf(14 to 2))
        assertEquals(100, CityBazaarOutlook.owedAt(s, 1, CLUB_TILE), "pays the 2 others 50 each")
        assertEquals(-100, CityBazaarOutlook.owedAt(s, 1, REST_HOUSE_TILE), "the 2 others pay 50 each")
        assertEquals(100, CityBazaarOutlook.owedAt(s, 1, 31), "wealth tax on 2 houses")
    }

    @Test
    fun `it values a city by keeping it from a rival who needs it for a set`() {
        val s = start().copy(owners = mapOf(14 to 2, 15 to 2))
        val buy = bot.BuyChoice(s, 1, 17, CityBazaarBoard.tile(17).price).evaluate(BuyTile)
        val ownGain = CityBazaarOutlook.future(s.copy(owners = s.owners + (17 to 1)), 1, 20) - CityBazaarOutlook.future(s, 1, 20)
        assertTrue(buy.gain > ownGain * 2, "denying the set is worth far more than the rent ($buy)")
    }

    @Test
    fun `in debt it won't sell the city that completes a rival's set`() {
        val inDebt = start().copy(
            currentSeat = 1, phase = CityBazaarPhase.DEBT, owners = mapOf(14 to 1, 10 to 1),
            cash = start().cash + (1 to 0), debt = CityBazaarDebt(700, mapOf(2 to 700), MoneyReason.RENT, 3),
        )
        assertEquals(14 to false, CityBazaarDebtPlanner.next(inDebt, 1), "alone, Shimla costs less to give up than Delhi")
        val rivalNeedsShimla = inDebt.copy(owners = inDebt.owners + mapOf(15 to 2, 17 to 2))
        assertEquals(10 to false, CityBazaarDebtPlanner.next(rivalNeedsShimla, 1), "but not when a rival would complete yellow with it")
    }

    @Test
    fun `it bids below price on a rival's debt auction`() {
        val s = start().copy(phase = CityBazaarPhase.AUCTION, currentSeat = 2, owners = mapOf(6 to 2), auction = CityBazaarAuction(6, 2, 750, null))
        assertTrue(bot.wantsToBid(s, 1))
    }

    @Test
    fun `heavy rent ahead makes running out of cash likely`() {
        val quiet = start().copy(positions = mapOf(1 to 1, 2 to 20, 3 to 20))
        val rivals = listOf(4, 6, 8, 10).associateWith { 2 }
        val heavy = quiet.copy(owners = rivals, levels = rivals.keys.associateWith { 4 })
        assertEquals(0.0, CityBazaarOutlook.ruinChance(quiet, 1, 1_000))
        val chance = CityBazaarOutlook.ruinChance(heavy, 1, 1_000)
        assertTrue(chance > 0.4, "chance $chance")
        assertTrue(CityBazaarOutlook.keepCash(heavy, 1, 500, 500, 10) > CityBazaarOutlook.keepCash(quiet, 1, 500, 500, 10))
    }

    @Test
    fun `a house is worth more just ahead of a rival who can't pay`() {
        val set = listOf(14, 15, 17).associateWith { 1 }
        fun building(rivalCash: Int): Double {
            val s = start().let { it.copy(owners = set, positions = mapOf(1 to 14, 2 to 10, 3 to 30), cash = it.cash + (2 to rivalCash)) }
            return bot.BuildChoice(s, 1, 14).evaluate(BuildOnTile(14)).gain
        }
        assertTrue(building(rivalCash = 100) > building(rivalCash = 50_000))
    }
}
