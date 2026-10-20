package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStatus
import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.application.match.actor.MatchResult
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.SeatPresence
import com.kglabs28.yapnplay.backend.contract.ViewerRole
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.rooms.SeatPlan
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_ID
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Matches on virtual time: bots, timers and away rules run in
 * milliseconds. The server referees: only the acting seat may move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchActorTest {

    private fun MatchTestKit.lastSnapshotFor(userId: String): MatchSnapshot =
        notifier.sent.last { it.first == userId && it.second.type == MessageType.MATCH_SNAPSHOT }
            .let { testJson.decodeFromJsonElement(MatchSnapshot.serializer(), it.second.payload) }

    @Test
    fun `a table of CPUs plays Royal Ludo to the end and the result is recorded`() = runTest {
        val kit = MatchTestKit(this)
        kit.service.startQuickPlay(ROYAL_LUDO_ID, emptyList(), cpuCount = 4)
        advanceTimeBy(HOURS_3)
        runCurrent()
        val result = kit.finished.single()
        assertEquals(4, result.seats.size)
        assertEquals(setOf(1, 2, 3, 4), result.seats.map { it.place }.toSet())
        val outcome = kit.outcomes.rows.getValue(result.matchId)
        assertEquals(MatchOutcomeStatus.COMPLETED, outcome.status)
        assertEquals(result.endedAt, outcome.endedAt)
        assertEquals(null, outcome.roomId)
    }

    @Test
    fun `a table of CPUs plays City Bazaar to the end`() = runTest {
        val kit = MatchTestKit(this)
        kit.service.startQuickPlay(CITY_BAZAAR_ID, emptyList(), cpuCount = 4)
        advanceTimeBy(HOURS_3)
        runCurrent()
        assertEquals(1, kit.finished.size)
    }

    @Test
    fun `only the acting player sees legal moves and may move`() = runTest {
        val kit = MatchTestKit(this)
        val matchId = kit.service.startQuickPlay(ROYAL_LUDO_ID, listOf("a", "b"), cpuCount = 2)
        val actor = assertNotNull(kit.service.find(matchId))
        // Play starts once both humans have opened the table.
        actor.ask("b", MatchAction.RequestSnapshot("b"))
        val snapshotA = assertIs<MatchResult.Snapshot>(actor.ask("a", MatchAction.RequestSnapshot("a"))).snapshot
        val seatA = assertNotNull(snapshotA.viewer.seat)
        val current = snapshotA.turn.seat
        val mover = if (current == seatA) "a" else snapshotA.seats.first { it.seat == current }.controllerUserId
        val waiter = if (mover == "a") "b" else "a"
        if (mover == "a" || mover == "b") {
            val moverSnap = assertIs<MatchResult.Snapshot>(actor.ask(mover, MatchAction.RequestSnapshot(mover))).snapshot
            assertTrue(moverSnap.legalMoves.isNotEmpty())
            val waiterSnap = assertIs<MatchResult.Snapshot>(actor.ask(waiter, MatchAction.RequestSnapshot(waiter))).snapshot
            assertTrue(waiterSnap.legalMoves.isEmpty())
            val refused = actor.ask(waiter, MatchAction.Play(MessageType.ROLL_DICE, JsonObject(emptyMap())))
            assertEquals(ErrorCode.NOT_YOUR_TURN, assertIs<MatchResult.Failed>(refused).code)
            assertEquals(MatchResult.Done, actor.ask(mover, MatchAction.Play(MessageType.ROLL_DICE, JsonObject(emptyMap()))))
        }
    }

    @Test
    fun `a player whose time runs out is played for, and three idle turns ask whether they are still there`() = runTest {
        val kit = MatchTestKit(this)
        val matchId = kit.service.startQuickPlay(ROYAL_LUDO_ID, listOf("a"), cpuCount = 3)
        val actor = assertNotNull(kit.service.find(matchId))
        actor.ask("a", MatchAction.RequestSnapshot("a"))
        advanceTimeBy(MINUTES_10)
        runCurrent()
        val snapshot = kit.lastSnapshotFor("a")
        // Either the check is running or it already went to the bot (Quick Play has no spectators).
        val seat = snapshot.seats.first { it.controllerUserId == "a" }
        assertTrue(snapshot.seatOffer != null || seat.presence == SeatPresence.BOT, "no absence handling: $snapshot")
    }

    @Test
    fun `away players are played at once and get their seat back`() = runTest {
        val kit = MatchTestKit(this)
        val matchId = kit.service.startQuickPlay(ROYAL_LUDO_ID, listOf("a"), cpuCount = 3)
        val actor = assertNotNull(kit.service.find(matchId))
        actor.ask("a", MatchAction.SetPresence(away = true))
        assertEquals(SeatPresence.AWAY, kit.lastSnapshotFor("a").seats.first { it.controllerUserId == "a" }.presence)
        advanceTimeBy(SECONDS_20)
        runCurrent()
        actor.ask("a", MatchAction.SetPresence(away = false))
        assertEquals(SeatPresence.PLAYING, kit.lastSnapshotFor("a").seats.first { it.controllerUserId == "a" }.presence)
    }

    @Test
    fun `in a room a present member can watch, outsiders cannot`() = runTest {
        val kit = MatchTestKit(this)
        val matchId = kit.service.start("room-1", SeatPlan(ROYAL_LUDO_ID, listOf("a", "b"), 2, emptyList(), emptyMap()))
        val actor = assertNotNull(kit.service.find(matchId))
        kit.present["watcher"] = "room-1"
        val watch = assertIs<MatchResult.Snapshot>(actor.ask("watcher", MatchAction.RequestSnapshot("Watcher"))).snapshot
        assertEquals(ViewerRole.SPECTATOR, watch.viewer.role)
        assertTrue(watch.legalMoves.isEmpty())
        val stranger = actor.ask("stranger", MatchAction.RequestSnapshot("Stranger"))
        assertEquals(ErrorCode.NOT_MEMBER, assertIs<MatchResult.Failed>(stranger).code)
    }

    @Test
    fun `a leaver gives the seat to a spectator who accepts`() = runTest {
        val kit = MatchTestKit(this)
        val matchId = kit.service.start("room-1", SeatPlan(ROYAL_LUDO_ID, listOf("a", "b"), 2, emptyList(), emptyMap()))
        val actor = assertNotNull(kit.service.find(matchId))
        kit.present["w"] = "room-1"
        actor.ask("w", MatchAction.RequestSnapshot("W"))
        assertEquals(MatchResult.Done, actor.ask("a", MatchAction.Leave("w")))
        assertEquals("w", kit.lastSnapshotFor("w").seatOffer?.userId)
        assertEquals(MatchResult.Done, actor.ask("w", MatchAction.AnswerOffer(accept = true)))
        val after = kit.lastSnapshotFor("w")
        assertEquals(ViewerRole.PLAYER, after.viewer.role)
        assertTrue(after.spectators.any { it.userId == "a" })
    }

    private companion object {
        const val HOURS_3 = 3 * 60 * 60_000L
        const val MINUTES_10 = 10 * 60_000L
        const val SECONDS_20 = 20_000L
    }
}
