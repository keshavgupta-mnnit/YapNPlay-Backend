package com.kglabs28.yapnplay.backend.application.match.games.doodleking

import com.kglabs28.yapnplay.backend.application.match.games.ExtensionOffer
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleExtend
import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.RelayOut
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleClear
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleKingWireAction
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleRelay
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleStroke
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleUndo
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleEnd
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleGuess
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingEngine
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingState
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePass
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePhase
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePick
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleTick
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Doodle King inside a match. The game runs on its own clock (every match
 * tick), so the match's turn timer never applies; anyone may guess at any
 * time. The drawing never goes through full updates: strokes come in as
 * side messages ([relay]), are kept on the [canvas] and forwarded at once.
 * The drawer is quiet for their whole turn ([silenced]).
 */
class DoodleKingSession(
    private val engine: DoodleKingEngine,
    private var state: DoodleKingState,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    /** TESTING ONLY: CPU seats play (admin setting), read on every tick. */
    private val testBots: () -> Boolean = { false },
    /** Rounds a "keep playing" vote adds (admin setting). */
    private val extensionRounds: () -> Int = { 1 },
) : GameSession {
    private val mapper = DoodleKingWireMapper(json)
    private val canvas = DoodleCanvas().apply { reset(state.drawingId) }
    private val bots = DoodleTestBots(random)

    override val currentSeat: Int get() = state.drawer
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == DoodlePhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) DoodleKingEngine.ranking(state) else emptyList()
    override val turnSeconds: Int get() = (state.dkRules.mandatory.drawMs / BackendConstants.MILLIS_PER_SECOND).toInt()
    override val thinkMillis: Long get() = 0L
    // The game's own clock runs every turn: the match's turn timer never does.
    override val waitsForTick: Boolean get() = true
    // Ending early is a vote any time but while the keep-playing vote runs.
    override val canEndEarly: Boolean get() = !isFinished && state.phase != DoodlePhase.LIMIT
    override val atLimit: Boolean get() = state.phase == DoodlePhase.LIMIT
    override val stillIn: Set<Int> get() = state.active.toSet()
    override val extension: ExtensionOffer get() = ExtensionOffer(extraRounds = extensionRounds())

    override fun colourOf(seat: Int): Colour = Colour.entries[(seat - 1) % Colour.entries.size]

    override fun decode(type: String, payload: JsonObject): Action? {
        if (type != MessageType.GAME_ACTION) return null
        val wire = payload[ACTION]?.jsonObject
            ?.let { runCatching { json.decodeFromJsonElement(DoodleKingWireAction.serializer(), it) }.getOrNull() }
            ?: return null
        val now = clock.nowMillis()
        return when (wire) {
            is DoodleKingWireAction.PickWord -> DoodlePick(wire.index, now)
            is DoodleKingWireAction.Guess -> DoodleGuess(wire.text, now)
            DoodleKingWireAction.Pass -> DoodlePass(now)
        }
    }

    /** Guesses come from everyone, whoever is drawing. */
    override fun actsOutOfTurn(seat: Int, action: Action): Boolean = true

    override fun apply(seat: Int, action: Action): List<GameEvent>? = take(engine.apply(state, SeatNumber(seat), action, random))

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? = null

    /**
     * Every match tick: test bots act, then phases move on and hints show. [cpuSeats]: seats
     * the server plays (CPUs, away or Autoplay players); [botSeats]: seats with nobody there.
     */
    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? {
        if (isFinished) return null
        val playing = if (testBots()) botSeats else emptySet()
        val events = mutableListOf<GameEvent>()
        bots.act(state, playing, now)?.let { (seat, action) -> apply(seat, action)?.let(events::addAll) }
        take(engine.apply(state, SeatNumber(state.drawer), DoodleTick(now, cpuSeats - playing), random))?.let(events::addAll)
        if (canvas.drawingId != state.drawingId) {
            canvas.reset(state.drawingId)
            // A test bot's turn to draw: its sketch is there at once.
            if (state.phase == DoodlePhase.DRAW && state.drawer in playing) bots.sketch(state.drawingId).forEach(canvas::addWhole)
            markInk()
        }
        return events.ifEmpty { null }
    }

    /**
     * The drawing's side messages. The drawer's strokes, undo and clear (only while drawing their
     * own turn) go to everyone else; anyone's sync request gets the whole drawing back.
     */
    override fun relay(seat: Int?, type: String, payload: JsonObject): RelayOut? {
        if (type == MessageType.DOODLE_SYNC) return RelayOut(toSender = wire(canvas.drawing()))
        if (seat != state.drawer || state.phase != DoodlePhase.DRAW) return null
        val message: DoodleRelay = runCatching {
            when (type) {
                MessageType.DOODLE_STROKE -> json.decodeFromJsonElement(DoodleStroke.serializer(), payload)
                MessageType.DOODLE_UNDO -> json.decodeFromJsonElement(DoodleUndo.serializer(), payload)
                MessageType.DOODLE_CLEAR -> json.decodeFromJsonElement(DoodleClear.serializer(), payload)
                else -> null
            }
        }.getOrNull() ?: return null
        val kept = when (message) {
            is DoodleStroke -> canvas.add(message)
            is DoodleUndo -> canvas.undo(message.drawingId)
            is DoodleClear -> canvas.clear(message.drawingId)
            else -> false
        }
        if (!kept) return null
        markInk()
        return RelayOut(toOthers = wire(message))
    }

    /** The drawer is quiet all their turn: from "your turn" to the word shown. */
    override fun silenced(seat: Int): Boolean = !isFinished && !atLimit && seat == state.drawer

    override fun endEarly(): List<GameEvent>? = take(engine.apply(state, SeatNumber(state.drawer), DoodleEnd, random))

    override fun extend(): List<GameEvent>? = take(engine.apply(state, SeatNumber(state.drawer), DoodleExtend(extensionRounds(), clock.nowMillis()), random))

    override fun endAtLimit(): List<GameEvent>? = endEarly()

    override fun pauseAfter(events: List<GameEvent>): Long = 0L

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as DoodleKingState
    }

    override fun publicState(viewerSeat: Int?): JsonObject = mapper.publicState(state, viewerSeat, canvas.strokeCount)

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> = emptyList()

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = mapper.events(events)

    /** The drawing isn't saved: after a restart the turn's canvas starts empty. */
    override fun save(): JsonObject = json.encodeToJsonElement(DoodleKingSaved.serializer(), DoodleKingSaved(state)).jsonObject

    /** Real lines on the canvas: the drawer isn't "drawing nothing" any more. */
    private fun markInk() {
        if (canvas.inked && !state.inked) state = state.copy(inked = true)
    }

    private fun wire(message: DoodleRelay): JsonObject = json.encodeToJsonElement(DoodleRelay.serializer(), message).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        state = result.state as DoodleKingState
        return result.events
    }

    private companion object {
        const val ACTION = "action"
    }
}

@Serializable
data class DoodleKingSaved(val engine: DoodleKingState)
