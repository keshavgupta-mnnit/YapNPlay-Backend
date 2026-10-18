package com.kglabs28.yapnplay.backend.games.base

import com.kglabs28.yapnplay.backend.games.base.play.GameBot

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.MatchOutcome
import com.kglabs28.yapnplay.backend.games.base.match.Viewer
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.base.rules.GameDescriptor
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.TimingConfig

/**
 * The game engine contract. One implementation per game module.
 *
 * Extends [GameDescriptor] so every game follows the same base guideline
 * (player limits, spectators, turn policy, rules) while the actual
 * implementation stays game-specific.
 *
 * Engines are PURE: no clock, no network, no hidden randomness.
 * Time and randomness are injected so tests are deterministic.
 */
interface Game : GameDescriptor {
    fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState
    fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult
    fun currentActor(state: GameState): SeatNumber
    /** The game's computer player (CPU seats, timeouts, autoplay). */
    val bot: GameBot

    /** Plays [seat]'s turn with [bot]'s choice; any roll uses [random] like a player's own. */
    fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult
    fun removeSeat(state: GameState, seat: SeatNumber): GameState
    /** What [viewer] may see — spectators get the public table only (see [GameView]). */
    fun viewFor(state: GameState, viewer: Viewer): GameView
    fun isFinished(state: GameState): Boolean
    fun outcome(state: GameState): MatchOutcome
}
