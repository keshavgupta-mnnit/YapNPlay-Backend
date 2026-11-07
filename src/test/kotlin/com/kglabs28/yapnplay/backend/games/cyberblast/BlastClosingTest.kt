package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The arena closes in when its time is up, or earlier once few players are left and most of the blockers are gone. */
class BlastClosingTest {
    private val random = object : GameRandom {
        private val r = Random(5)
        override fun nextInt(bound: Int): Int = r.nextInt(bound)
    }

    private fun player(col: Int, row: Int, seat: Int) = BlastPlayer(seat, BlastGrid.centreX(BlastGrid.of(col, row)), BlastGrid.centreY(BlastGrid.of(col, row)), lives = 5, radius = 1)

    private fun board(crates: Int, startCrates: Int, time: Long = 10_000, players: Int = 2) = CyberBlastState(
        gameId = GameId(CYBER_BLAST_ID),
        blastRules = CyberBlastRules(CyberBlastMandatoryRules(closeAfterMs = 300_000, closeWhenCratesLeftPct = 25, closeWhenPlayersLeft = 2)),
        seatCount = players,
        players = (1..players).map { player(5 + it, 5, it) },
        crates = (0 until crates).map { BlastGrid.of(2 + it % 10, 3 + it / 10 * 2) }.toSet(),
        startCrates = startCrates,
        phase = BlastPhase.PLAYING,
        time = time,
    )

    @Test
    fun `with most blockers still standing it waits for the timer`() {
        val s = board(crates = 10, startCrates = 20)
        val (next, _) = BlastClosing.update(s, random)
        assertEquals(0L, next.nextCloseAt)
        assertEquals(290_000L, BlastClosing.startsIn(next))
    }

    @Test
    fun `with few blockers but many players still in it waits for the timer`() {
        val s = board(crates = 5, startCrates = 20, players = 3)
        val (next, _) = BlastClosing.update(s, random)
        assertEquals(0L, next.nextCloseAt)
    }

    @Test
    fun `two players left and a quarter or fewer of the blockers it starts closing at once`() {
        val s = board(crates = 5, startCrates = 20)
        val (started, _) = BlastClosing.update(s, random)
        assertTrue(started.nextCloseAt > 0L, "closing did not start")
        assertNull(BlastClosing.startsIn(started))
        val (next, _) = BlastClosing.update(started, random)
        assertEquals(1, next.closed)
    }

    @Test
    fun `an unknown starting count closes on the timer only`() {
        val s = board(crates = 0, startCrates = 0)
        val (next, _) = BlastClosing.update(s, random)
        assertEquals(0L, next.nextCloseAt)
    }
}
