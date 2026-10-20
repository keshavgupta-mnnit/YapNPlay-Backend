package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.games.citybazaar.CityBazaarSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BidPlaced
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CardDrawn
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.DiceRolled
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.MoneyMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.SentToJail
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TokenMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TurnPassed
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import com.kglabs28.yapnplay.backend.support.testJson
import kotlin.test.Test
import kotlin.test.assertEquals

/** The next turn waits for the dice, the hop and ~2.5 s per message on the table (messages capped at 8 s). */
class CityBazaarPacingTest {
    private val session = CityBazaarSessionFactory(CityBazaarEngine(), object : GameRandom {
        override fun nextInt(bound: Int) = 0
    }, testJson, Clock { 0L }).start(2, null)

    private val roll = DiceRolled(1, 3, 4)
    private val hop = TokenMoved(1, 0, 7, 7, jump = false)
    private val rent = MoneyMoved(1, 2, 100, MoneyReason.RENT, 7)

    @Test
    fun `roll and hop only`() = assertEquals(2_100L + 7 * 200 + 500, session.pauseAfter(listOf(roll, hop, TurnPassed(2))))

    @Test
    fun `each message adds 2_5 s`() =
        assertEquals(2_100L + 7 * 200 + 500 + 5_000, session.pauseAfter(listOf(roll, hop, rent, CardDrawn(1, "c1"))))

    @Test
    fun `messages are capped at 8 s and bids add nothing`() =
        assertEquals(8_000L, session.pauseAfter(listOf(rent, rent, rent, SentToJail(1), BidPlaced(1, 3, 100))))
}
