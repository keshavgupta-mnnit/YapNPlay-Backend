package com.kglabs28.yapnplay.backend.games.base.match

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules

/**
 * The full state of a match from the game engine's perspective.
 * Each game module defines its own state class implementing this interface.
 * The server holds the state and passes it back to the engine for every action.
 */
interface GameState {
    val gameId: GameId
    val rules: GameRules
    /** Active seats (removed seats are no longer in this list). */
    val seats: List<SeatNumber>
    /** Whose turn it is and what phase of the turn. */
    val turn: TurnState
}

/**
 * The current turn: whose seat and what phase (game-specific).
 */
interface TurnState {
    val seat: SeatNumber
    /** Game-specific phase label, e.g., "ROLL" or "MOVE" for Royal Ludo. */
    val phase: String
}
