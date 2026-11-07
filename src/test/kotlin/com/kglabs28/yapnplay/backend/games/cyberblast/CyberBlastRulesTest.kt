package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Cyber Blast's rules: bombs, fire, points, hearts, safe comebacks, boots, the end. */
class CyberBlastRulesTest {
    private val engine = CyberBlastEngine()
    private val random = object : GameRandom {
        private val r = Random(7)
        override fun nextInt(bound: Int): Int = r.nextInt(bound)
    }

    private fun player(seat: Int, col: Int, row: Int, lives: Int = 5, radius: Int = 2) = BlastPlayer(
        seat = seat,
        x = BlastGrid.centreX(BlastGrid.of(col, row)),
        y = BlastGrid.centreY(BlastGrid.of(col, row)),
        lives = lives,
        radius = radius,
    )

    private fun board(vararg players: BlastPlayer, crates: Set<Int> = emptySet()) = CyberBlastState(
        gameId = GameId(CYBER_BLAST_ID),
        blastRules = CyberBlastRules(CyberBlastMandatoryRules(itemEveryMs = 1_000_000)),
        seatCount = players.size,
        players = players.toList(),
        crates = crates,
        phase = BlastPhase.PLAYING,
        nextItemAt = 1_000_000,
    )

    private fun CyberBlastState.act(seat: Int, action: com.kglabs28.yapnplay.backend.games.base.play.Action): Pair<CyberBlastState, List<GameEvent>> {
        val result = engine.apply(this, SeatNumber(seat), action, random)
        assertIs<ApplyResult.Success>(result)
        return result.state as CyberBlastState to result.events
    }

    private fun CyberBlastState.wait(ms: Long) = act(1, BlastAdvance(ms))

    @Test
    fun `a bomb breaks the first blocker each way for 10 points and its fire stops there`() {
        val crate = BlastGrid.of(2, 0)
        val behind = BlastGrid.of(3, 0)
        val start = board(player(1, 0, 0, radius = 3), player(2, 14, 10), crates = setOf(crate, behind))
        val (dropped, _) = start.act(1, BlastDropBomb())
        val (moved, _) = dropped.act(1, BlastSteer(BlastDir.DOWN))
        val (after, events) = moved.wait(3_100)
        assertFalse(crate in after.crates)
        assertTrue(behind in after.crates)
        assertEquals(10, after.player(1)!!.score)
        assertTrue(events.any { it is BlastBoom && crate in it.crates })
    }

    @Test
    fun `catching another player scores 100, grows the blast by one and costs them 25 and a heart`() {
        val start = board(player(1, 0, 0), player(2, 2, 0))
        val (after, events) = start.copy(bombs = listOf(BlastBomb(1, BlastGrid.of(0, 2), 1, 2, 10))).wait(50)
        // The fire from (0,2) goes up to (0,0): seat 1 is caught by their own bomb.
        val hit = events.filterIsInstance<BlastHit>().single()
        assertTrue(hit.self)
        assertEquals(-15, after.player(1)!!.score)
        val (later, more) = board(player(1, 0, 0), player(2, 2, 0)).copy(bombs = listOf(BlastBomb(1, BlastGrid.of(1, 0), 1, 2, 10))).wait(50)
        val hits = more.filterIsInstance<BlastHit>()
        val onTwo = hits.single { it.victim == 2 }
        assertEquals(100, onTwo.byPoints)
        assertEquals(4, later.player(2)!!.lives)
        assertEquals(-25, later.player(2)!!.score)
        assertEquals(2, later.player(1)!!.radius)
        assertEquals(1, later.player(1)!!.streak)
        assertEquals(1, later.player(2)!!.radius)
        assertEquals(100 - 15, later.player(1)!!.score)
        assertTrue(later.player(2)!!.shielded(later.time))
    }

    @Test
    fun `metal pillars stop the fire`() {
        val start = board(player(1, 0, 0), player(2, 1, 2))
        // (1,1) is a pillar between the bomb at (1,0) and seat 2 at (1,2).
        val (after, events) = start.copy(bombs = listOf(BlastBomb(1, BlastGrid.of(1, 0), 2, 3, 10))).wait(50)
        assertTrue(events.filterIsInstance<BlastHit>().none { it.victim == 2 })
        assertTrue(after.flames.none { it.tile == BlastGrid.of(1, 2) })
    }

    @Test
    fun `a blast sets off any bomb it reaches at once`() {
        val start = board(player(1, 14, 10), player(2, 14, 8)).copy(
            bombs = listOf(BlastBomb(1, BlastGrid.of(4, 0), 1, 2, 10), BlastBomb(2, BlastGrid.of(6, 0), 2, 2, 99_999)),
        )
        val (after, _) = start.wait(50)
        assertTrue(after.bombs.isEmpty())
        assertTrue(after.flames.any { it.tile == BlastGrid.of(8, 0) })
    }

    @Test
    fun `someone hit comes back on a tile with no fire coming and a way out of their own blast`() {
        val crates = BlastGrid.tiles(15, 11).filter { !BlastGrid.isPillar(it) && it % 3 == 0 }.toSet()
        val start = board(player(1, 0, 0), player(2, 14, 10), crates = crates - BlastGrid.of(0, 0) - BlastGrid.of(14, 10))
        repeat(20) {
            val (s, tile) = BlastSpawns.respawn(start, 1, random)
            assertTrue(BlastGrid.isOpen(s, tile))
            assertTrue(BlastSpawns.hasEscape(s, tile, 2, BlastDanger.of(s)), "no way out from $tile")
        }
    }

    @Test
    fun `every player starts where they can get away from their first bomb`() {
        repeat(10) {
            val s = engine.start(CyberBlastRules(), 8, random) as CyberBlastState
            assertEquals(8, s.players.map { it.tile }.toSet().size)
            s.players.forEach { assertTrue(BlastSpawns.hasEscape(s, it.tile, s.mandatory.startRadius, emptyMap()), "seat ${it.seat} trapped") }
        }
    }

    @Test
    fun `walking onto the speed boots makes you faster for 30 seconds`() {
        val boots = BlastGrid.of(1, 0)
        val start = board(player(1, 0, 0), player(2, 14, 10)).copy(items = listOf(BlastItem(boots, BlastItemKind.SPEED, 10_000)))
        val (moving, _) = start.act(1, BlastSteer(BlastDir.RIGHT))
        val (after, events) = moving.wait(400)
        assertTrue(events.any { it is BlastBoosted && it.seat == 1 })
        assertTrue(after.player(1)!!.boosted(after.time + 29_000))
        assertTrue(after.items.isEmpty())
    }

    @Test
    fun `walking slides round a pillar corner instead of sticking`() {
        // Half a tile off the row, pushing right into the pillar at (1,1): it slides up onto row 0.
        val p = player(1, 0, 0).copy(y = BlastGrid.centreY(BlastGrid.of(0, 0)) + 300)
        val start = board(p, player(2, 14, 10))
        val (moving, _) = start.act(1, BlastSteer(BlastDir.RIGHT))
        val (after, _) = moving.wait(1_000)
        assertTrue(after.player(1)!!.x > p.x)
        assertEquals(BlastGrid.centreY(BlastGrid.of(0, 0)), after.player(1)!!.y)
    }

    @Test
    fun `the last player with hearts left wins and the rest rank by points`() {
        val start = board(player(1, 0, 0), player(2, 4, 0, lives = 1), player(3, 14, 10, lives = 1).copy(score = 300))
        val (mid, _) = start.copy(bombs = listOf(BlastBomb(1, BlastGrid.of(4, 2), 1, 2, 10))).wait(50)
        assertEquals(BlastPhase.PLAYING, mid.phase)
        val (end, events) = mid.copy(bombs = listOf(BlastBomb(2, BlastGrid.of(14, 8), 1, 2, mid.time + 10))).wait(50)
        assertEquals(BlastPhase.FINISHED, end.phase)
        assertTrue(events.any { it is BlastFinished })
        assertEquals(listOf(1, 3, 2), CyberBlastEngine.ranking(end))
    }

    @Test
    fun `no bomb before the countdown, but then as many as you like, one per tile`() {
        val waiting = board(player(1, 0, 0), player(2, 14, 10)).copy(phase = BlastPhase.COUNTDOWN)
        assertIs<ApplyResult.Rejected>(engine.apply(waiting, SeatNumber(1), BlastDropBomb(), random))
        val (one, _) = board(player(1, 0, 0), player(2, 14, 10)).act(1, BlastDropBomb())
        assertIs<ApplyResult.Rejected>(engine.apply(one, SeatNumber(1), BlastDropBomb(), random))
        assertNotEquals(0, one.bombs.size)
        // Four bombs down one after another along the top row.
        var s = one
        repeat(3) { i ->
            s = s.copy(players = s.players.map { if (it.seat == 1) it.copy(x = BlastGrid.centreX(BlastGrid.of((i + 1) * 2, 0))) else it })
            s = s.act(1, BlastDropBomb()).first
        }
        assertEquals(4, s.bombsOf(1))
    }

    @Test
    fun `a streak of hits grows the blast and losing a heart starts it again at 1`() {
        val hunter = player(1, 14, 0, radius = 1).copy(streak = 2, radius = 3)
        val (after, _) = board(hunter, player(2, 0, 10)).copy(bombs = listOf(BlastBomb(1, BlastGrid.of(14, 2), 2, 2, 10))).wait(50)
        assertEquals(0, after.player(1)!!.streak)
        assertEquals(1, after.player(1)!!.radius)
    }

    @Test
    fun `a bomb goes on the tile the player sees themselves on when it is right beside the server's, never further`() {
        val start = board(player(1, 2, 0), player(2, 14, 10))
        val (beside, _) = start.act(1, BlastDropBomb(BlastGrid.of(3, 0)))
        assertEquals(BlastGrid.of(3, 0), beside.bombs.single().tile)
        val (far, _) = start.act(1, BlastDropBomb(BlastGrid.of(6, 0)))
        assertEquals(BlastGrid.of(2, 0), far.bombs.single().tile)
    }

    @Test
    fun `pushing between two ways goes the side way when the main way is blocked`() {
        // On the top row: up is the edge, so up-and-right walks right.
        val start = board(player(1, 2, 0), player(2, 14, 10))
        val (pushed, _) = start.act(1, BlastSteer(BlastDir.UP, BlastDir.RIGHT))
        val (after, _) = pushed.wait(300)
        assertTrue(after.player(1)!!.x > start.player(1)!!.x)
        assertEquals(start.player(1)!!.y, after.player(1)!!.y)
    }

    @Test
    fun `blockers start mirrored left to right and top to bottom`() {
        val crates = BlastLayout.crates(15, 11, 60, random)
        crates.forEach { t ->
            assertTrue(BlastGrid.of(14 - BlastGrid.col(t), BlastGrid.row(t)) in crates)
            assertTrue(BlastGrid.of(BlastGrid.col(t), 10 - BlastGrid.row(t)) in crates)
        }
    }

    @Test
    fun `the closing arena turns the outside ring into walls and crushes whoever is there`() {
        val start = board(player(1, 0, 0), player(2, 14, 10)).copy(
            blastRules = CyberBlastRules(CyberBlastMandatoryRules(closeAfterMs = 100, closeStepMs = 50, itemEveryMs = 1_000_000)),
        )
        val (after, events) = start.wait(300)
        assertTrue(BlastGrid.of(0, 0) in after.walls)
        assertTrue(events.any { it is BlastHit && it.crushed && it.victim == 1 })
        assertTrue(after.player(1)!!.tile !in after.walls)
    }
}
