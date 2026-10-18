package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.*
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.*
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RoyalLudoDeterminismTest {
    private val engine = RoyalLudoEngine()

    @Test
    fun `same seed and actions give identical states`() {
        val actions = listOf(RollDice, MovePiece(PieceId("0")), RollDice, MovePiece(PieceId("1")))
        val s1 = playGame(12345, actions) as RoyalLudoState
        val s2 = playGame(12345, actions) as RoyalLudoState
        assertEquals(s1.pieces, s2.pieces)
        assertEquals(s1.currentSeat, s2.currentSeat)
        assertEquals(s1.phase, s2.phase)
    }

    @Test
    fun `state survives serialise and restore`() {
        val actions = listOf(RollDice, MovePiece(PieceId("0")), RollDice, MovePiece(PieceId("1")))
        val state = playGame(999, actions) as RoyalLudoState
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val serialised = json.encodeToString(state)
        val restored = json.decodeFromString<RoyalLudoState>(serialised)
        assertEquals(state.pieces, restored.pieces)
        assertEquals(state.currentSeat, restored.currentSeat)
        assertEquals(state.phase, restored.phase)
    }

    private fun playGame(seed: Int, actions: List<Action>): GameState {
        val random = SeededRandom(seed.toLong())
        var state = engine.start(RoyalLudoRules.TRADITIONAL, 2, random)
        for (action in actions) {
            val s = state as RoyalLudoState
            val result = engine.apply(state, SeatNumber(s.currentSeat), action, random)
            if (result is ApplyResult.Success) state = result.state
        }
        return state
    }
}
