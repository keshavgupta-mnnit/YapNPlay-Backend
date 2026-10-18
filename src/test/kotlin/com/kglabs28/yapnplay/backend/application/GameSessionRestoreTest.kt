package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.citybazaar.CityBazaarSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.royalludo.RoyalLudoSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.support.testJson
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** A game saved mid-match comes back the same (what a server restart relies on). */
class GameSessionRestoreTest {
    private val random = Random(SEED)
    private val gameRandom = object : GameRandom {
        override fun nextInt(bound: Int): Int = random.nextInt(bound)
    }
    private val clock = Clock { NOW }

    @Test
    fun `Royal Ludo comes back with the same board and turn`() =
        assertRoundTrip(RoyalLudoSessionFactory(RoyalLudoEngine(), gameRandom, testJson))

    @Test
    fun `City Bazaar comes back with the same cash, tiles and turn`() =
        assertRoundTrip(CityBazaarSessionFactory(CityBazaarEngine(), gameRandom, testJson, clock), houseRules = mapOf("time_limit" to false))

    private fun assertRoundTrip(factory: GameSessionFactory, houseRules: Map<String, Boolean>? = null) {
        val session = factory.start(SEATS, houseRules)
        repeat(BOT_TURNS) { if (!session.isFinished) session.botPlay(session.currentSeat, BotMode.FULL) }

        val restored = factory.restore(session.save())

        assertEquals(session.currentSeat, restored.currentSeat)
        assertEquals(session.phase, restored.phase)
        (1..SEATS).forEach { assertEquals(session.colourOf(it), restored.colourOf(it)) }
        assertEquals(session.publicState(1), restored.publicState(1))
        assertEquals(session.save(), restored.save())
        // And it keeps playing.
        restored.botPlay(restored.currentSeat, BotMode.FULL)
    }

    private companion object {
        const val SEED = 11
        const val SEATS = 4
        const val BOT_TURNS = 40
        const val NOW = 1_000_000L
    }
}
