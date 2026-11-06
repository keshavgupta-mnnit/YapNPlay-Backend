package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBot
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBotSettings
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastGoalDecision
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Cyber Blast bot chooses like a person: it runs from fire, never bombs itself into a corner, and breaks blockers. */
class BlastBotTest {
    private val engine = CyberBlastEngine()
    private val random = object : GameRandom {
        private val r = Random(11)
        override fun nextInt(bound: Int): Int = r.nextInt(bound)
    }
    private val settings = BlastBotSettings()

    private fun at(col: Int, row: Int, seat: Int) = BlastPlayer(seat, BlastGrid.centreX(BlastGrid.of(col, row)), BlastGrid.centreY(BlastGrid.of(col, row)), lives = 5, radius = 2)

    private fun board(vararg players: BlastPlayer, crates: Set<Int> = emptySet()) = CyberBlastState(
        gameId = GameId(CYBER_BLAST_ID),
        blastRules = CyberBlastRules(CyberBlastMandatoryRules(itemEveryMs = 1_000_000)),
        seatCount = players.size,
        players = players.toList(),
        crates = crates,
        phase = BlastPhase.PLAYING,
        nextItemAt = 1_000_000,
    )

    @Test
    fun `standing in a bomb's line it walks out of the fire's way`() {
        val s = board(at(4, 0, 1), at(14, 10, 2)).copy(bombs = listOf(BlastBomb(1, BlastGrid.of(6, 0), 2, 3, 1_500)))
        val goal = BlastGoalDecision(s, s.player(1)!!, settings, bombReady = false).choose()!!
        val fire = BlastGrid.reach(s, BlastGrid.of(6, 0), 3).fire.map { it.first }.toSet()
        assertFalse(goal.tile in fire, "it stays in the blast at ${goal.tile}")
    }

    @Test
    fun `boxed in with no way out of its own blast it never drops a bomb`() {
        // A dead end at (0,0): blockers at (2,0) and (0,2), only (1,0) and (0,1) free, all inside a radius-2 blast.
        val s = board(at(0, 0, 1), at(14, 10, 2), crates = setOf(BlastGrid.of(2, 0), BlastGrid.of(0, 2)))
        val decision = BlastGoalDecision(s, s.player(1)!!, settings, bombReady = true)
        assertFalse(decision.choose()!!.bomb)
    }

    @Test
    fun `next to blockers with a way out it bombs them`() {
        val s = board(at(4, 4, 1), at(14, 10, 2), crates = setOf(BlastGrid.of(6, 4), BlastGrid.of(4, 6)))
        val goal = BlastGoalDecision(s, s.player(1)!!, settings, bombReady = true).choose()!!
        assertTrue(goal.bomb || goal.steps <= 2, "it wanders off to $goal")
    }

    @Test
    fun `bots playing each other survive their own bombs for a while`() {
        var s = engine.start(CyberBlastRules(), 4, random) as CyberBlastState
        val startCrates = s.crates.size
        val bot = BlastBot({ settings }, random)
        var selfHits = 0
        repeat(600) {
            bot.act(s, setOf(1, 2, 3, 4)).forEach { (seat, action) ->
                (engine.apply(s, SeatNumber(seat), action, random) as? ApplyResult.Success)?.let { s = it.state as CyberBlastState }
            }
            val result = engine.apply(s, SeatNumber(1), BlastAdvance(50), random) as? ApplyResult.Success ?: return@repeat
            s = result.state as CyberBlastState
            selfHits += result.events.count { it is BlastHit && it.self && !it.crushed }
        }
        // 30 s of play: bombs went down, blockers broke, and few were their own fault.
        assertTrue(s.crates.size < startCrates, "no blocker broke")
        assertTrue(s.players.sumOf { it.score } != 0, "nobody scored anything")
        assertTrue(selfHits <= 4, "bots blew themselves up $selfHits times")
        assertEquals(4, s.players.size)
    }
}
