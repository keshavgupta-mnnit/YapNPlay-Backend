package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.application.match.actor.MatchActor
import com.kglabs28.yapnplay.backend.application.match.actor.MatchResult
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_ID
import com.kglabs28.yapnplay.backend.support.MatchTestKit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The end-game vote: a person asks, play pauses, the other people decide; CPUs never count. */
@OptIn(ExperimentalCoroutinesApi::class)
class EndVoteTest {

    private suspend fun TestScope.start(humans: List<String>): Pair<MatchTestKit, MatchActor> {
        val kit = MatchTestKit(this)
        val actor = assertNotNull(kit.service.find(kit.service.startQuickPlay(CITY_BAZAAR_ID, humans, cpuCount = 4 - humans.size)))
        humans.forEach { actor.ask(it, MatchAction.RequestSnapshot(it)) }
        runCurrent()
        return kit to actor
    }

    private suspend fun MatchActor.snapshot(user: String): MatchSnapshot =
        assertIs<MatchResult.Snapshot>(ask(user, MatchAction.RequestSnapshot(user))).snapshot

    @Test
    fun `two people - play pauses, and both saying yes ends the match`() = runTest {
        val (_, actor) = start(listOf("a", "b"))
        assertEquals(MatchResult.Done, actor.ask("a", MatchAction.ProposeEnd))
        val paused = actor.snapshot("b")
        val vote = assertNotNull(paused.endVote)
        assertEquals(2, vote.voters.size)
        assertEquals(1, vote.yes.size)
        assertNull(paused.turn.deadline, "the turn timer is frozen")
        actor.ask("b", MatchAction.VoteEnd(agree = true))
        runCurrent()
        assertTrue(actor.isFinished)
    }

    @Test
    fun `a no keeps playing, and asking again too soon is refused`() = runTest {
        val (_, actor) = start(listOf("a", "b"))
        actor.ask("a", MatchAction.ProposeEnd)
        actor.ask("b", MatchAction.VoteEnd(agree = false))
        val after = actor.snapshot("a")
        assertNull(after.endVote)
        assertEquals(false, actor.isFinished)
        val again = assertIs<MatchResult.Failed>(actor.ask("a", MatchAction.ProposeEnd))
        assertEquals(ErrorCode.RATE_LIMITED, again.code)
    }

    @Test
    fun `nobody answering in time keeps the match going`() = runTest {
        val (_, actor) = start(listOf("a", "b"))
        actor.ask("a", MatchAction.ProposeEnd)
        advanceTimeBy(21_000)
        runCurrent()
        assertNull(actor.snapshot("a").endVote)
        assertEquals(false, actor.isFinished)
    }

    @Test
    fun `a lone player with CPUs ends it alone`() = runTest {
        val (_, actor) = start(listOf("a"))
        actor.ask("a", MatchAction.ProposeEnd)
        runCurrent()
        assertTrue(actor.isFinished)
    }
}
