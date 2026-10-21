package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclineTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import kotlin.test.Test
import kotlin.test.assertEquals

/** A player's Autoplay plays the whole turn: it buys and pays out of jail where a CPU would hold back. */
class CityBazaarAutoplayProfileTest {
    private val engine = CityBazaarEngine()
    private val autoplay = CityBazaarBot(CityBazaarBotProfile.AUTOPLAY)

    private fun state(): CityBazaarState = engine.start(CityBazaarRules.STANDARD, 3, object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }) as CityBazaarState

    @Test
    fun `on a quiet board with cash to spare, Autoplay and the CPU buy, the timeout bot never does`() {
        val tile = 14
        val price = CityBazaarBoard.tile(tile).price
        val s = state().copy(phase = CityBazaarPhase.BUY, pendingTile = tile, currentSeat = 1).let { it.copy(cash = it.cash + (1 to price + 2_500)) }
        assertEquals(BuyTile, autoplay.chooseAction(s, SeatNumber(1)))
        assertEquals(BuyTile, engine.bot.chooseAction(s, SeatNumber(1)))
        assertEquals(DeclineTile, engine.cautiousBot.chooseAction(s, SeatNumber(1)))
    }
}
