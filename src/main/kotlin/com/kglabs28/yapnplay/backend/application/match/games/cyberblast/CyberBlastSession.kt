package com.kglabs28.yapnplay.backend.application.match.games.cyberblast

import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.FrameOut
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.RelayOut
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberInput
import com.kglabs28.yapnplay.backend.contract.cyberblast.CyberRelay
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastAdvance
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastDir
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastDropBomb
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastEnd
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastFinished
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastHit
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastOut
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPhase
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastStarted
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastSteer
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastEngine
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastState
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBot
import com.kglabs28.yapnplay.backend.games.cyberblast.bot.BlastBotSettings
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Cyber Blast inside a match: a real-time game. The match calls [frame]
 * every [frameMillis]; each frame the server's seats (CPUs, players away)
 * steer and bomb, the board moves on, and the whole board goes to the table
 * as a light `CyberFrame` relay. Steering and bombs come in as `CyberInput`
 * relays at any moment. A full update (and a saved checkpoint) follows only
 * when something big happens: a heart lost, a player out, the start, the end.
 */
class CyberBlastSession(
    private val engine: CyberBlastEngine,
    private var state: CyberBlastState,
    private val random: GameRandom,
    private val json: Json,
    private val frameMs: Long,
    botSettings: () -> BlastBotSettings,
) : GameSession {
    private val mapper = CyberBlastWireMapper(json)
    private val bot = BlastBot(botSettings, random)
    /** When the last frame ran (server clock); null until the first (and after a restart). */
    private var lastNow: Long? = null
    private var frameNumber = 0L
    /** How many blockers / walls the table was last sent, so a frame carries them only when they change. */
    private var sentCrates = -1
    private var sentWalls = -1

    override val currentSeat: Int get() = state.turn.seat.value
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == BlastPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) CyberBlastEngine.ranking(state) else emptyList()
    override val turnSeconds: Int get() = TURN_SECONDS
    override val thinkMillis: Long get() = 0L
    // Its own clock runs everything: the match's turn timer never does.
    override val waitsForTick: Boolean get() = true
    override val canEndEarly: Boolean get() = !isFinished
    override val frameMillis: Long get() = frameMs
    override val botTrace: List<BotTraceEntry> get() = bot.trace.recent()

    override fun colourOf(seat: Int): Colour = Colour.entries[(seat - 1) % Colour.entries.size]

    /** Everything comes in as `CyberInput` relays, nothing as a game action. */
    override fun decode(type: String, payload: JsonObject): Action? = null

    override fun actsOutOfTurn(seat: Int, action: Action): Boolean = true

    override fun apply(seat: Int, action: Action): List<GameEvent>? = take(engine.apply(state, SeatNumber(seat), action, random))

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? = null

    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? = null

    override fun frame(now: Long, cpuSeats: Set<Int>): FrameOut? {
        if (isFinished) return null
        // A pause (a vote, a slow server) never fast-forwards the board: at most a short catch-up.
        val elapsed = lastNow?.let { (now - it).coerceIn(0, MAX_CATCH_UP_MS) } ?: 0L
        lastNow = now
        state.players.filter { it.seat !in cpuSeats }.forEach { bot.forget(it.seat) }
        bot.act(state, cpuSeats).forEach { (seat, action) -> take(engine.apply(state, SeatNumber(seat), action, random)) }
        val events = take(engine.apply(state, SeatNumber(FIRST_SEAT), BlastAdvance(elapsed), random)).orEmpty()
        frameNumber++
        val withCrates = state.crates.size != sentCrates
        val withWalls = state.walls.size != sentWalls
        sentCrates = state.crates.size
        sentWalls = state.walls.size
        val frame = mapper.frame(state, frameNumber, events, withCrates, withWalls)
        val big = events.filter { it is BlastHit || it is BlastOut || it is BlastStarted || it is BlastFinished }
        return FrameOut(json.encodeToJsonElement(CyberRelay.serializer(), frame).jsonObject, big)
    }

    /** A player's steering and bombs: kept at once, nothing forwarded (the next frame shows it). */
    override fun relay(seat: Int?, type: String, payload: JsonObject): RelayOut? {
        if (type != MessageType.CYBER_INPUT || seat == null || isFinished) return null
        val input = runCatching { json.decodeFromJsonElement(CyberInput.serializer(), payload) }.getOrNull() ?: return null
        val me = state.player(seat)?.takeIf { it.inPlay } ?: return RelayOut()
        val dir = BlastDir.of(input.dir)
        if (dir != me.held) take(engine.apply(state, SeatNumber(seat), BlastSteer(dir), random))
        // A bomb that can't go down (none left, one already there) is simply not dropped.
        if (input.bomb) take(engine.apply(state, SeatNumber(seat), BlastDropBomb(input.tile), random))
        return RelayOut()
    }

    override fun endEarly(): List<GameEvent>? = take(engine.apply(state, SeatNumber(FIRST_SEAT), BlastEnd, random))

    override fun pauseAfter(events: List<GameEvent>): Long = 0L

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as CyberBlastState
        bot.forget(seat)
    }

    override fun publicState(viewerSeat: Int?): JsonObject =
        mapper.publicState(state, mapper.frame(state, frameNumber, emptyList(), withCrates = true, withWalls = true), frameMs)

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> = emptyList()

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = mapper.events(events)

    override fun save(): JsonObject = json.encodeToJsonElement(CyberBlastSaved.serializer(), CyberBlastSaved(state)).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        state = result.state as CyberBlastState
        return result.events
    }

    private companion object {
        const val TURN_SECONDS = 60
        const val FIRST_SEAT = 1
        /** The longest a single frame moves the board on (ms). */
        const val MAX_CATCH_UP_MS = 250L
    }
}

@Serializable
data class CyberBlastSaved(val engine: CyberBlastState)
