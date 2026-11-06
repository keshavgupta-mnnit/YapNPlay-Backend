package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.application.match.games.cyberblast.CyberBlastSessionFactory
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberBlastPublicState
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A room's Cyber Blast toggles: Big Arena, Mini-map, Fewer / More Blockers, and fewer blockers with more players. */
class CyberBlastRoomOptionsTest {
    private val json = Json { encodeDefaults = true }
    private val factory = CyberBlastSessionFactory(CyberBlastEngine(), object : GameRandom {
        private val r = Random(3)
        override fun nextInt(bound: Int): Int = r.nextInt(bound)
    }, json)

    private fun table(seats: Int, rules: Map<String, Boolean>?): CyberBlastPublicState =
        json.decodeFromJsonElement(CyberBlastPublicState.serializer(), factory.start(seats, rules).publicState(1))

    /** Blockers on the board, averaged over a few starts (spawns clear a few at random). */
    private fun blockers(seats: Int, rules: Map<String, Boolean>?): Double = (1..5).map { table(seats, rules).frame.crates!!.size }.average()

    @Test
    fun `quick play is the usual 15 by 11 arena with no mini-map`() {
        val t = table(4, null)
        assertEquals(15, t.cols)
        assertEquals(11, t.rows)
        assertFalse(t.miniMap)
    }

    @Test
    fun `big arena is twice as big and everyone starts inside it`() {
        val t = table(6, mapOf(BLAST_BIG_ARENA_KEY to true, BLAST_MINI_MAP_KEY to true))
        assertEquals(BLAST_BIG_COLS, t.cols)
        assertEquals(BLAST_BIG_ROWS, t.rows)
        assertTrue(t.miniMap)
        t.frame.players.forEach {
            assertTrue(BlastGrid.col(BlastGrid.tileAt(it.x, it.y)) < BLAST_BIG_COLS && BlastGrid.row(BlastGrid.tileAt(it.x, it.y)) < BLAST_BIG_ROWS)
        }
    }

    @Test
    fun `fewer blockers opens the arena and more blockers packs it`() {
        val usual = blockers(4, emptyMap())
        assertTrue(blockers(4, mapOf(BLAST_FEWER_BLOCKERS_KEY to true)) < usual)
        assertTrue(blockers(4, mapOf(BLAST_MORE_BLOCKERS_KEY to true)) > usual)
        // Both on: they cancel out, the usual amount.
        val both = blockers(4, mapOf(BLAST_FEWER_BLOCKERS_KEY to true, BLAST_MORE_BLOCKERS_KEY to true))
        assertTrue(both > blockers(4, mapOf(BLAST_FEWER_BLOCKERS_KEY to true)) && both < blockers(4, mapOf(BLAST_MORE_BLOCKERS_KEY to true)))
    }

    @Test
    fun `more players get a slightly more open arena`() {
        assertTrue(blockers(8, emptyMap()) < blockers(2, emptyMap()))
    }
}
