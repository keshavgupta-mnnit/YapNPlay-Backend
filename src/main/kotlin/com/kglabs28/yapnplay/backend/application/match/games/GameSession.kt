package com.kglabs28.yapnplay.backend.application.match.games

import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import kotlinx.serialization.json.JsonObject

/**
 * One running game inside a match: the engine state plus anything only
 * this game needs (City Bazaar's auction clock). The match actor handles
 * everything common to all games (seats, timers, presence, handover) and
 * talks to the game only through this interface.
 */
interface GameSession {
    val currentSeat: Int
    /** Phase name sent in `TurnInfo.phase` (e.g. ROLL, MOVE, BUY). */
    val phase: String
    val isFinished: Boolean
    /** Seats from first to last; empty until finished. */
    val ranking: List<Int>
    /** Turn time from the game's timing config, in seconds. */
    val turnSeconds: Int
    /** Pause before a bot acts, so it looks like someone thinking. */
    val thinkMillis: Long
    /** True while the game runs on its own clock (an auction): the turn timer waits. */
    val waitsForTick: Boolean

    fun colourOf(seat: Int): Colour

    /** Turns a match-channel message into an engine action, or null when this game has no such action. */
    fun decode(type: String, payload: JsonObject): Action?

    /** Applies [action] for [seat]; null when the engine refuses it. */
    fun apply(seat: Int, action: Action): List<GameEvent>?

    /** Plays [seat]'s turn for them. */
    fun botPlay(seat: Int, mode: BotMode): List<GameEvent>?

    /**
     * The game's own clock, called every match tick: time limits, auction
     * closing, CPU bids. [cpuSeats] are seats the server plays fully.
     */
    fun tick(now: Long, cpuSeats: Set<Int>): List<GameEvent>?

    /** How long the table needs to animate [events] before the next action. */
    fun pauseAfter(events: List<GameEvent>): Long

    fun removeSeat(seat: Int)

    /** `MatchSnapshot.publicState` for a viewer ([viewerSeat] null: spectator). */
    fun publicState(viewerSeat: Int?): JsonObject

    fun legalMoves(viewerSeat: Int?): List<LegalMove>

    /** Engine events as wire JSON (each with `"type"`), dropping internal ones. */
    fun wireEvents(events: List<GameEvent>): List<JsonObject>

    /** Everything needed to rebuild this session after a restart ([GameSessionFactory.restore]). */
    fun save(): JsonObject
}
