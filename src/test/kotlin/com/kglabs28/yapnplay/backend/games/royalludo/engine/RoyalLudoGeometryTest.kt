package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.royalludo.utils.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoyalLudoGeometryTest {

    @Test
    fun `track has 52 distinct squares`() {
        assertEquals(52, (0 until TRACK_SIZE).toList().toSet().size)
    }

    @Test
    fun `colour start squares are correct`() {
        assertEquals(0, RoyalLudoBoardUtils.startSquareFor(0))
        assertEquals(13, RoyalLudoBoardUtils.startSquareFor(1))
        assertEquals(26, RoyalLudoBoardUtils.startSquareFor(2))
        assertEquals(39, RoyalLudoBoardUtils.startSquareFor(3))
    }

    @Test
    fun `progress 0 maps to start square`() {
        assertEquals(RED_START, RoyalLudoBoardUtils.absoluteSquare(RED_START, 0))
        assertEquals(GREEN_START, RoyalLudoBoardUtils.absoluteSquare(GREEN_START, 0))
        assertEquals(YELLOW_START, RoyalLudoBoardUtils.absoluteSquare(YELLOW_START, 0))
        assertEquals(BLUE_START, RoyalLudoBoardUtils.absoluteSquare(BLUE_START, 0))
    }

    @Test
    fun `progress wraps around track`() {
        assertEquals(50, RoyalLudoBoardUtils.absoluteSquare(RED_START, 50))
        assertEquals(4, RoyalLudoBoardUtils.absoluteSquare(YELLOW_START, 30))
    }

    @Test
    fun `star squares are safe`() {
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(8))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(21))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(34))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(47))
    }

    @Test
    fun `start squares are safe`() {
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(RED_START))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(GREEN_START))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(YELLOW_START))
        assertTrue(RoyalLudoBoardUtils.isSafeSquare(BLUE_START))
    }

    @Test
    fun `other squares are not safe`() {
        assertFalse(RoyalLudoBoardUtils.isSafeSquare(1))
        assertFalse(RoyalLudoBoardUtils.isSafeSquare(5))
        assertFalse(RoyalLudoBoardUtils.isSafeSquare(10))
    }

    @Test
    fun `home column length is 5`() {
        assertEquals(5, HOME_COLUMN_LENGTH)
    }

    @Test
    fun `progress 51-55 is in home column`() {
        assertTrue(RoyalLudoBoardUtils.isInHomeColumn(51))
        assertTrue(RoyalLudoBoardUtils.isInHomeColumn(55))
        assertFalse(RoyalLudoBoardUtils.isInHomeColumn(50))
        assertFalse(RoyalLudoBoardUtils.isInHomeColumn(56))
    }
}
