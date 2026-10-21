package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Ludo bot weighs gain against the chance of being hit, and follows the match's pair rule. */
class RoyalLudoSmartBotTest {
    private val bot = RoyalLudoBot()

    /** Seat 1 is red (squares = progress); seat 2 is [enemyColour]. Seat 1 to move with [roll]. */
    private fun state(mine: List<Int>, enemy: List<Int>, roll: Int, enemyColour: Int = 1, rules: RoyalLudoRules = RoyalLudoRules.TRADITIONAL) =
        RoyalLudoState(
            _gameId = GameId("royal-ludo"), _rules = rules,
            _seats = listOf(SeatNumber(1), SeatNumber(2)),
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_MOVE.name),
            seatColours = mapOf(1 to 0, 2 to enemyColour),
            pieces = mapOf(1 to mine, 2 to enemy),
            currentSeat = 1, phase = Phase.AWAITING_MOVE, lastRoll = roll,
            legalPieces = mine.indices.filter { mine[it] >= 0 },
        )

    @Test
    fun `quick play has no pair protection, rooms may switch it on`() {
        assertFalse(RoyalLudoRules.TRADITIONAL.house.pairProtection)
        assertTrue(RoyalLudoRules.TRADITIONAL.isAllowed(PlayMode.PUBLIC_MATCH))
        assertTrue(RoyalLudoHouseRules().pairProtection)
    }

    @Test
    fun `it won't land 2 squares ahead of an enemy when a safer move exists`() {
        // Green piece on square 21 (green progress 8). Red 20→23 lands 2 ahead of it; 30→33 is 12 away.
        val s = state(mine = listOf(20, 30, PROGRESS_IN_BASE, PROGRESS_IN_BASE), enemy = listOf(8, PROGRESS_IN_BASE, PROGRESS_IN_BASE, PROGRESS_IN_BASE), roll = 3)
        assertEquals(1, bot.bestPiece(s))
    }

    @Test
    fun `two pieces on one square are not safe in quick play, but are in a room with the pair rule`() {
        // Red pieces 0 and 1 together on square 10; green piece 2 behind it (green progress 47 = square 8).
        val mine = listOf(10, 10, 30, PROGRESS_IN_BASE)
        val enemy = listOf(47, PROGRESS_IN_BASE, PROGRESS_IN_BASE, PROGRESS_IN_BASE)
        assertEquals(0, bot.bestPiece(state(mine, enemy, roll = 3)), "no pair rule: move a piece of the hittable pair to safety (square 13)")
        val withPairs = RoyalLudoRules(house = RoyalLudoHouseRules(pairProtection = true))
        assertEquals(2, bot.bestPiece(state(mine, enemy, roll = 3, rules = withPairs)), "pair rule on: keep the pair, move the other piece")
    }

    @Test
    fun `a piece under a 1 in 6 threat is moved away rather than advancing another`() {
        // Yellow piece on square 9 (yellow progress 35), 3 behind red's piece on 12.
        val s = state(mine = listOf(12, 40, PROGRESS_IN_BASE, PROGRESS_IN_BASE), enemy = listOf(35, PROGRESS_IN_BASE, PROGRESS_IN_BASE, PROGRESS_IN_BASE), roll = 5, enemyColour = 2)
        assertEquals(0, bot.bestPiece(s))
    }
}
