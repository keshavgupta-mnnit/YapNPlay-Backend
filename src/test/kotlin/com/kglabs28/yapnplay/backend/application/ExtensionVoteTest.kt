package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.actor.MatchResult
import com.kglabs28.yapnplay.backend.application.match.MatchSetup
import com.kglabs28.yapnplay.backend.application.match.SeatHolder
import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.application.match.actor.MatchActor
import com.kglabs28.yapnplay.backend.application.match.actor.MatchSeatState
import com.kglabs28.yapnplay.backend.application.match.actor.MatchState
import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.ExtensionOffer
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.SeatPresence
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The shared keep-playing vote: humans the server isn't playing for decide; half or more "yes" goes on. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionVoteTest {

    /** A game sitting at its limit; records whether it was extended or ended. */
    private class AtLimitGame : GameSession {
        var outcome: String? = null
        override val currentSeat = 1
        override val phase get() = if (outcome == null) "EXTENSION_VOTE" else "ROLL"
        override val isFinished get() = outcome == "ended"
        override val ranking get() = if (isFinished) listOf(1, 2, 3) else emptyList()
        override val turnSeconds = 10
        override val thinkMillis = 1_000L
        override val waitsForTick get() = outcome == null
        override val atLimit get() = outcome == null
        override val stillIn = setOf(1, 2, 3)
        override val extension = ExtensionOffer(extraRounds = 30)
        override fun extend(): List<GameEvent> = emptyList<GameEvent>().also { outcome = "extended" }
        override fun endAtLimit(): List<GameEvent> = emptyList<GameEvent>().also { outcome = "ended" }
        override fun colourOf(seat: Int) = Colour.entries[seat - 1]
        override fun decode(type: String, payload: JsonObject): Action? = null
        override fun apply(seat: Int, action: Action): List<GameEvent>? = null
        override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? = null
        override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? = null
        override fun pauseAfter(events: List<GameEvent>) = 0L
        override fun removeSeat(seat: Int) = Unit
        override fun publicState(viewerSeat: Int?) = JsonObject(emptyMap())
        override fun legalMoves(viewerSeat: Int?): List<LegalMove> = emptyList()
        override fun wireEvents(events: List<GameEvent>): List<JsonObject> = emptyList()
        override fun save() = JsonObject(emptyMap())
    }

    /** Seats: 1 "a" and 2 "b" humans, 3 a CPU. */
    private fun TestScope.start(game: AtLimitGame): Pair<MatchTestKit, MatchActor> {
        val kit = MatchTestKit(this)
        val setup = MatchSetup("m-1", "test", null, "Quick Play", listOf(SeatHolder("a", "A", 1), SeatHolder("b", "B", 1)), 1, null)
        val seats = listOf(
            MatchSeatState(1, "a", "A", 1, Colour.RED, cpu = false),
            MatchSeatState(2, "b", "B", 1, Colour.GREEN, cpu = false),
            MatchSeatState(3, "cpu-1", "CPU 1", 1, Colour.YELLOW, cpu = true, presence = SeatPresence.BOT),
        )
        val actor = MatchActor(MatchState(setup, game, seats.toMutableList(), 0L), kit.deps, backgroundScope) {}
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        return kit to actor
    }

    private suspend fun MatchActor.snapshot(user: String) =
        assertIs<MatchResult.Snapshot>(ask(user, MatchAction.RequestSnapshot(user))).snapshot

    private fun MatchTestKit.lastEventTypes(): List<String> = notifier.sent.last().second.let {
        testJson.decodeFromJsonElement(MatchSnapshot.serializer(), it.payload).events.map { e -> e["type"].toString().trim('"') }
    }

    @Test
    fun `the humans vote, the CPU does not, and half saying yes goes on`() = runTest {
        val game = AtLimitGame()
        val (kit, actor) = start(game)
        val vote = actor.snapshot("a").extensionVote!!
        assertEquals(listOf(1, 2), vote.voters)
        assertEquals(30, vote.extraRounds)
        assertNull(vote.extraMinutes)

        assertEquals(MatchResult.Done, actor.ask("a", MatchAction.VoteExtension(keepPlaying = true)))
        val again = actor.ask("a", MatchAction.VoteExtension(keepPlaying = false))
        assertEquals(ErrorCode.ILLEGAL_MOVE, assertIs<MatchResult.Failed>(again).code)
        actor.ask("b", MatchAction.VoteExtension(keepPlaying = false))
        assertEquals("extended", game.outcome)
        assertEquals(listOf("ExtensionVoted"), kit.lastEventTypes())
        assertNull(actor.snapshot("a").extensionVote)
    }

    @Test
    fun `no votes before the time is up ends the match`() = runTest {
        val game = AtLimitGame()
        val (_, actor) = start(game)
        actor.snapshot("a")
        advanceTimeBy(11_000)
        runCurrent()
        assertEquals("ended", game.outcome)
    }

    @Test
    fun `nobody but the vote's voters may vote`() = runTest {
        val (_, actor) = start(AtLimitGame())
        // Not a person at the table (a CPU seat's id): refused as not in the match.
        val stranger = actor.ask("cpu-1", MatchAction.VoteExtension(keepPlaying = true))
        assertEquals(ErrorCode.NOT_MEMBER, assertIs<MatchResult.Failed>(stranger).code)
        assertEquals(MessageType.VOTE_EXTENSION, "VoteExtension")
    }

    @Test
    fun `a lone human with CPUs decides alone, even while the bot holds their seat`() = runTest {
        val kit = MatchTestKit(this)
        val setup = MatchSetup("m-2", "test", null, "Quick Play", listOf(SeatHolder("a", "A", 1)), 3, null)
        val seats = mutableListOf(
            MatchSeatState(1, "a", "A", 1, Colour.RED, cpu = false, presence = SeatPresence.BOT),
            MatchSeatState(2, "cpu-1", "CPU 1", 1, Colour.GREEN, cpu = true, presence = SeatPresence.BOT),
            MatchSeatState(3, "cpu-2", "CPU 2", 1, Colour.YELLOW, cpu = true, presence = SeatPresence.BOT),
        )
        val inner = AtLimitGame()
        val actor = MatchActor(MatchState(setup, inner, seats, 0L), kit.deps, backgroundScope) {}
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertEquals(listOf(1), actor.snapshot("a").extensionVote!!.voters)
        assertEquals(MatchResult.Done, actor.ask("a", MatchAction.VoteExtension(keepPlaying = true)))
        assertEquals("extended", inner.outcome)
    }
}
