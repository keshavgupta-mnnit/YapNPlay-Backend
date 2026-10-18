package com.kglabs28.yapnplay.backend.games.base.play

import com.kglabs28.yapnplay.backend.games.base.match.GameState

/**
 * Result of applying an action or autoplay: the new state plus events,
 * or a typed rejection if the action was not legal.
 */
sealed interface ApplyResult {
    data class Success(val state: GameState, val events: List<GameEvent>) : ApplyResult
    data class Rejected(val rejection: Rejection) : ApplyResult
}
