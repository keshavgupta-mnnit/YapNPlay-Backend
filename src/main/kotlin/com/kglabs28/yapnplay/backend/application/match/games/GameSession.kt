package com.kglabs28.yapnplay.backend.application.match.games

import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
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
    fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int> = cpuSeats): List<GameEvent>?

    /**
     * True while the game waits at its round or time limit with 2+ players
     * still in (no clear winner): the match asks the humans whether to go on.
     * A game that sets it must also report [waitsForTick] (no turn timer).
     */
    val atLimit: Boolean get() = false

    /** Seats still in the game (not out or bankrupt): who may vote at the limit. */
    val stillIn: Set<Int> get() = emptySet()

    /** What "keep playing" adds at this limit. */
    val extension: ExtensionOffer get() = ExtensionOffer()

    /** They voted to go on: the extra rounds/minutes are added and play resumes. */
    fun extend(): List<GameEvent>? = null

    /** They voted to stop (or nobody voted yes): the match ends as at its limit. */
    fun endAtLimit(): List<GameEvent>? = null

    /** The bots' recent decisions in this match, newest first (admin page: why did the bot do that). */
    val botTrace: List<BotTraceEntry> get() = emptyList()

    /** Players may vote to end the match early right now (false: the game has no early end, or it is mid-auction). */
    val canEndEarly: Boolean get() = false

    /** The end-game vote passed: the match ends now, ranked as the game ranks an early end. */
    fun endEarly(): List<GameEvent>? = null

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
