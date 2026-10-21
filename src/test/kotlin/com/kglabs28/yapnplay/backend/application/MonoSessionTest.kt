package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.games.mono.MonoChaosSessionFactory
import com.kglabs28.yapnplay.backend.application.match.games.mono.MonoSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoEvent
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoEngine
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosEngine
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** MONO and Complete Chaos hosted by the server: saved and restored, hands hidden, the MONO! clock, CPU games to the end. */
@OptIn(ExperimentalCoroutinesApi::class)
class MonoSessionTest {
    private var now = 1_000_000L
    private val clock = Clock { now }
    private val random = object : GameRandom {
        private val r = Random(3)
        override fun nextInt(bound: Int) = r.nextInt(bound)
    }

    @Test
    fun `both games come back the same after a restart`() {
        listOf(MonoSessionFactory(MonoEngine(), random, testJson, clock), MonoChaosSessionFactory(MonoChaosEngine(), random, testJson, clock)).forEach { factory ->
            val session = factory.start(4, null)
            repeat(10) { session.botPlay(session.currentSeat, com.kglabs28.yapnplay.backend.application.match.games.BotMode.FULL) }
            val restored = factory.restore(session.save())
            assertEquals(session.publicState(1), restored.publicState(1))
            assertEquals(session.currentSeat, restored.currentSeat)
        }
    }

    @Test
    fun `only the player who drew sees the card, and nobody sees another hand`() {
        val session = MonoSessionFactory(MonoEngine(), random, testJson, clock).start(3, null)
        val drawer = session.currentSeat
        val events = assertNotNull(session.apply(drawer, MonoAction.DrawCard))
        val own = session.wireEventsFor(events, drawer).first()
        assertEquals("CardDrawn", own["type"]!!.jsonPrimitive.content)
        val other = session.wireEventsFor(events, drawer % 3 + 1).first()
        assertEquals("CardsDrawn", other["type"]!!.jsonPrimitive.content)
        val hands = session.publicState(2)["hands"]!!.jsonObject
        assertEquals(setOf("2"), hands.keys)
    }

    @Test
    fun `a seat the server plays always calls MONO! as it goes down to one card`() {
        val engine = MonoEngine()
        val factory = MonoSessionFactory(engine, random, testJson, clock)
        // Bots play until one goes down to a single card.
        val session = factory.start(2, null)
        var windowEvents = emptyList<com.kglabs28.yapnplay.backend.games.base.play.GameEvent>()
        var guard = 0
        while (guard++ < 400 && !session.isFinished) {
            val seat = session.currentSeat
            val events = session.botPlay(seat, com.kglabs28.yapnplay.backend.application.match.games.BotMode.FULL).orEmpty()
            if (events.any { it is MonoEvent.MonoCalled }) { windowEvents = events; break }
        }
        assertTrue(windowEvents.any { it is MonoEvent.MonoCalled }, "the server's seat called MONO! itself")
    }

    @Test
    fun `CPU-only MONO and Complete Chaos tables play to the end`() = runTest {
        listOf(MonoConstants.ID, "mono-chaos").forEach { gameId -> cpuTablePlaysOut(gameId) }
    }

    private suspend fun TestScope.cpuTablePlaysOut(gameId: String) {
        val kit = MatchTestKit(this)
        val actor = assertNotNull(kit.service.find(kit.service.startQuickPlay(gameId, emptyList(), cpuCount = 4)))
        var steps = 0
        while (!actor.isFinished && steps++ < MAX_STEPS) {
            advanceTimeBy(STEP_MS)
            runCurrent()
        }
        assertTrue(actor.isFinished, "$gameId finished")
        assertEquals(1, kit.finished.size)
    }

    private companion object {
        const val STEP_MS = 1_000L
        const val MAX_STEPS = 20_000
    }
}
