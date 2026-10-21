package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.domain.identity.Avatars
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StartAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Debts are paid by auction by default (house rule "debt_auction"); rent paid is totalled; people never get robot avatars. */
class CityBazaarDebtAuctionTest {
    private val engine = CityBazaarEngine()
    private val random = object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }

    private fun inDebt(debtAuction: Boolean): CityBazaarState {
        val rules = CityBazaarRules.STANDARD.copy(house = CityBazaarHouseRules(debtAuction = debtAuction))
        val s = engine.start(rules, 3, random) as CityBazaarState
        return s.copy(
            currentSeat = 1, phase = CityBazaarPhase.DEBT, owners = mapOf(14 to 1),
            debt = CityBazaarDebt(50_000, mapOf(2 to 50_000), MoneyReason.RENT, 3),
        )
    }

    @Test
    fun `selling a city to pay a debt puts it up for auction`() {
        val result = assertIs<ApplyResult.Success>(engine.apply(inDebt(true), SeatNumber(1), SellTile(14), random))
        val s = result.state as CityBazaarState
        assertEquals(CityBazaarPhase.AUCTION, s.phase)
        assertEquals(14, s.auction?.tile)
        assertTrue(result.events.any { it is AuctionStarted })
    }

    @Test
    fun `with the rule off it goes straight to the bank`() {
        val result = assertIs<ApplyResult.Success>(engine.apply(inDebt(false), SeatNumber(1), SellTile(14), random))
        val s = result.state as CityBazaarState
        assertEquals(null, s.owners[14])
        assertTrue(result.events.any { it is TileSold })
    }

    @Test
    fun `a bare city may be sold while other cities still have buildings`() {
        val s = inDebt(false).copy(owners = mapOf(14 to 1, 17 to 1), levels = mapOf(17 to 4))
        assertIs<ApplyResult.Success>(engine.apply(s, SeatNumber(1), SellTile(14), random))
        assertIs<ApplyResult.Success>(engine.apply(inDebt(true).copy(owners = mapOf(14 to 1, 17 to 1), levels = mapOf(17 to 4)), SeatNumber(1), StartAuction(14), random))
    }

    @Test
    fun `rent paid is counted for the payer`() {
        val s0 = (engine.start(CityBazaarRules.STANDARD, 3, random) as CityBazaarState).copy(owners = mapOf(1 to 2), positions = mapOf(1 to 35, 2 to 0, 3 to 0))
        val dice = object : GameRandom {
            override fun nextInt(bound: Int) = 0
        }
        val s = (assertIs<ApplyResult.Success>(engine.apply(s0, SeatNumber(1), RollDice, dice)).state as CityBazaarState)
        assertEquals(1, s.positions[1])
        assertEquals(s.rentBySeat[2], s.rentPaidBySeat[1])
        assertTrue((s.rentPaidBySeat[1] ?: 0) > 0)
    }

    @Test
    fun `new players never get a robot avatar`() {
        val picks = (0 until 1_000).map { i -> Avatars.random { from, until -> from + i % (until - from) } }.toSet()
        assertTrue(picks.none { it in BackendConstants.CPU_AVATARS })
        assertEquals(BackendConstants.AVATAR_COUNT - BackendConstants.CPU_AVATARS.size, picks.size)
    }
}
