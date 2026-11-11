package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.royalludo.utils.RoyalLudoTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The track for 4, 6 and 8 arms, and which board a number of players gets. */
class RoyalLudoGeometryTest {
    private val square = RoyalLudoTrack(4)

    @Test
    fun `the square board is the classic 52-square track`() {
        assertEquals(52, square.size)
        assertEquals(50, square.enterHome)
        assertEquals(56, square.finish)
        assertEquals(listOf(0, 13, 26, 39), (0 until 4).map { square.start(it) })
    }

    @Test
    fun `squares wrap round the track`() {
        assertEquals(0, square.square(0, 0))
        assertEquals(50, square.square(0, 50))
        assertEquals(4, square.square(26, 30))
    }

    @Test
    fun `start and star squares are safe, others are not`() {
        listOf(0, 8, 13, 21, 26, 34, 39, 47).forEach { assertTrue(square.isSafe(it), "square $it") }
        listOf(1, 7, 9, 12, 14, 51).forEach { assertFalse(square.isSafe(it), "square $it") }
    }

    @Test
    fun `the hexagon and octagon keep 13 squares an arm and a 5-square home column`() {
        val hexagon = RoyalLudoTrack(6)
        assertEquals(78, hexagon.size)
        assertEquals(82, hexagon.finish)
        assertEquals(65, hexagon.start(5))
        assertTrue(hexagon.isSafe(73))
        val octagon = RoyalLudoTrack(8)
        assertEquals(104, octagon.size)
        assertEquals(108, octagon.finish)
        assertTrue(octagon.isInHomeColumn(103))
        assertFalse(octagon.isOnSharedTrack(103))
    }

    @Test
    fun `how many play picks the board`() {
        assertEquals(listOf(4, 4, 4, 6, 6, 8, 8), (2..8).map { RoyalLudoTrack.forPlayers(it).arms })
    }

    @Test
    fun `seven players get the octagon with their colours round it, three pieces with the house rule`() {
        val rules = RoyalLudoRules(house = RoyalLudoHouseRules(threePieces = true))
        val s = RoyalLudoEngine().start(rules, 7, SeededRandom(3L)) as RoyalLudoState
        assertEquals(8, s.arms)
        assertEquals(7, s.seatColours.values.toSet().size)
        assertTrue(s.pieces.values.all { it.size == 3 })
        // Four players keep four pieces even with the rule on.
        val four = RoyalLudoEngine().start(rules, 4, SeededRandom(3L)) as RoyalLudoState
        assertTrue(four.pieces.values.all { it.size == 4 })
    }

    @Test
    fun `room CPU fill tops up to the board's size`() {
        val engine = RoyalLudoEngine()
        assertEquals(listOf(4, 4, 4, 6, 6, 8, 8), (2..8).map { engine.roomTableSize(it) })
        assertEquals(null, engine.featureNeeded(4))
        assertEquals("royal-ludo-8", engine.featureNeeded(6))
    }
}
