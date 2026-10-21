package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_ID
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Turning Autoplay on during your own turn plays that turn after the short bot pause, not at the turn deadline. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoplayNowTest {

    private fun MatchTestKit.snapshotsFor(user: String): List<MatchSnapshot> = notifier.sent
        .filter { it.first == user && it.second.type == MessageType.MATCH_SNAPSHOT }
        .map { testJson.decodeFromJsonElement(MatchSnapshot.serializer(), it.second.payload) }

    private suspend fun TestScope.autoplayPlaysAtOnce(gameId: String) {
        val kit = MatchTestKit(this)
        val actor = assertNotNull(kit.service.find(kit.service.startQuickPlay(gameId, listOf("a"), cpuCount = 3)))
        actor.ask("a", MatchAction.RequestSnapshot("a"))
        // Let the CPUs play until it is a's turn, waiting for a roll.
        var mine: MatchSnapshot? = null
        repeat(MAX_STEPS) {
            if (mine != null) return@repeat
            advanceTimeBy(STEP_MS)
            runCurrent()
            mine = kit.snapshotsFor("a").lastOrNull()?.takeIf { s ->
                val seat = s.viewer.seat
                s.turn.seat == seat && s.turn.deadline != null && s.turn.phase.endsWith("ROLL")
            }
        }
        val turn = assertNotNull(mine, "a's turn never came")
        val seat = turn.viewer.seat!!
        val sentBefore = kit.notifier.sent.size

        actor.ask("a", MatchAction.SetAutoplay(enabled = true))
        // Before the turn timer runs out (it starts after the table's reading pause), so this is Autoplay, not the timeout bot.
        advanceTimeBy(turn.turn.deadline!! - turn.serverTime - BEFORE_DEADLINE_MS)
        runCurrent()
        val after = kit.notifier.sent.drop(sentBefore)
            .filter { it.first == "a" && it.second.type == MessageType.MATCH_SNAPSHOT }
            .map { testJson.decodeFromJsonElement(MatchSnapshot.serializer(), it.second.payload) }
        val rolled = after.any { s -> s.events.any { e -> e["type"].toString().trim('"') == "Rolled" && e["seat"].toString() == seat.toString() } }
        assertTrue(rolled, "$gameId: autoplay did not play a's turn before the deadline")
    }

    @Test
    fun `royal ludo plays the turn at once`() = runTest { autoplayPlaysAtOnce(ROYAL_LUDO_ID) }

    @Test
    fun `city bazaar plays the turn at once`() = runTest { autoplayPlaysAtOnce(CITY_BAZAAR_ID) }

    private companion object {
        const val STEP_MS = 250L
        const val MAX_STEPS = 2_000
        const val BEFORE_DEADLINE_MS = 1_000L
    }
}
