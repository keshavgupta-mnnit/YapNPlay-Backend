package com.kglabs28.yapnplay.backend.application.match.games.royalludo

import com.kglabs28.yapnplay.backend.application.match.games.BotThinkTime
import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoBot
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoPublicState
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoWireEvent
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.PieceId
import com.kglabs28.yapnplay.backend.games.royalludo.engine.DiceRolled
import com.kglabs28.yapnplay.backend.games.royalludo.engine.Phase
import com.kglabs28.yapnplay.backend.games.royalludo.engine.PieceCaptured
import com.kglabs28.yapnplay.backend.games.royalludo.engine.PieceMoved
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoState
import com.kglabs28.yapnplay.backend.games.royalludo.engine.TurnForfeited
import com.kglabs28.yapnplay.backend.games.royalludo.engine.TurnPassed
import com.kglabs28.yapnplay.backend.games.royalludo.engine.TurnReverted
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Royal Ludo inside a match: the engine plus its wire shapes and animation timing. */
class RoyalLudoSession(
    private val engine: RoyalLudoEngine,
    private var state: RoyalLudoState,
    private val random: GameRandom,
    private val json: Json,
    override val turnSeconds: Int = GameSettings.RL_TURN_SECONDS.default,
    private val think: BotThinkTime = BotThinkTime(
        GameSettings.RL_CPU_THINK_MS.default.toLong(),
        GameSettings.RL_CPU_THINK_JITTER_MS.default.toLong(),
        GameSettings.RL_CPU_DECISION_EXTRA_MS.default.toLong(),
    ),
    /** How the server plays a seat (values from the runtime settings), and its recent decisions. */
    private val bots: RoyalLudoSessionBots = RoyalLudoSessionBots(),
) : GameSession {
    override val botTrace: List<BotTraceEntry> get() = bots.trace.recent()


    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = phaseName()
    override val isFinished: Boolean get() = state.phase == Phase.FINISHED
    override val ranking: List<Int>
        get() = if (isFinished) engine.outcome(state).ranking.sortedBy { it.place }.map { it.seat.value } else emptyList()
    override val waitsForTick: Boolean = false
    /** A bot's think time: longer when more than one piece can move. */
    override val thinkMillis: Long get() = think.millis(state.phase == Phase.AWAITING_MOVE && state.legalPieces.size > 1)

    override fun colourOf(seat: Int): Colour = Colour.entries[state.colourForSeat(seat)]

    override fun decode(type: String, payload: JsonObject): Action? = when (type) {
        MessageType.ROLL_DICE -> RollDice
        MessageType.MOVE_PIECE -> payload[PIECE_ID]?.jsonPrimitive?.contentOrNull?.let { MovePiece(PieceId(it)) }
        MessageType.GAME_ACTION -> payload[ACTION]?.jsonObject?.let { action ->
            decode(action[TYPE]?.jsonPrimitive?.contentOrNull.orEmpty(), action)
        }
        else -> null
    }

    override fun apply(seat: Int, action: Action): List<GameEvent>? =
        take(engine.apply(state, SeatNumber(seat), action, random))

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? =
        take(engine.autoplayWith(bots.bot, state, SeatNumber(seat), random))

    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? = null

    override fun pauseAfter(events: List<GameEvent>): Long = events.sumOf { event ->
        when (event) {
            is DiceRolled -> AFTER_ROLL_MS
            is PieceMoved -> PER_STEP_MS * stepsOf(event) + AFTER_MOVE_MS
            is PieceCaptured, is TurnReverted -> AFTER_CAPTURE_MS
            else -> 0L
        }
    }

    override val canEndEarly: Boolean get() = !isFinished

    /**
     * Ended by the players' vote (or every human left): the match ends here,
     * finishers first, then by how far each seat's pieces got.
     */
    override fun endEarly(): List<GameEvent>? {
        if (isFinished) return null
        state = state.copy(phase = Phase.FINISHED, legalPieces = emptyList())
        return emptyList()
    }

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as RoyalLudoState
    }

    override fun publicState(viewerSeat: Int?): JsonObject = json.encodeToJsonElement(
        RoyalLudoPublicState.serializer(),
        RoyalLudoPublicState(
            // A seat removed from the game (left Quick Play) has no pieces on the board.
            pieces = state.pieces.filterKeys { it !in state.removalOrder },
            phase = phaseName(),
            lastRoll = state.lastRoll,
            legalPieces = if (isMyMove(viewerSeat)) state.legalPieces else emptyList(),
            finishedSeats = state.finishingOrder,
            ranking = ranking,
        ),
    ).jsonObject

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> = when {
        viewerSeat == null || viewerSeat != state.currentSeat -> emptyList()
        state.phase == Phase.AWAITING_ROLL -> listOf(LegalMove.RollDice)
        state.phase == Phase.AWAITING_MOVE -> state.legalPieces.map { LegalMove.MovePiece(it.toString()) }
        else -> emptyList()
    }

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = events.mapNotNull { event ->
        when (event) {
            is DiceRolled -> RoyalLudoWireEvent.Rolled(event.seat, event.value)
            is PieceMoved -> RoyalLudoWireEvent.Moved(event.seat, event.piece, event.fromProgress, event.toProgress)
            is PieceCaptured -> RoyalLudoWireEvent.Captured(event.seat, event.victim, event.victimPiece)
            is TurnPassed -> RoyalLudoWireEvent.TurnPassed(event.seat)
            is TurnForfeited -> RoyalLudoWireEvent.TurnPassed(event.seat)
            is TurnReverted -> RoyalLudoWireEvent.Reverted(event.seat, event.pieces)
            else -> null
        }?.let { json.encodeToJsonElement(RoyalLudoWireEvent.serializer(), it).jsonObject }
    }

    override fun save(): JsonObject = json.encodeToJsonElement(RoyalLudoState.serializer(), state).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        state = result.state as RoyalLudoState
        return result.events
    }

    private fun isMyMove(viewerSeat: Int?) = viewerSeat == state.currentSeat && state.phase == Phase.AWAITING_MOVE

    private fun phaseName(): String = when (state.phase) {
        Phase.AWAITING_ROLL -> PHASE_ROLL
        Phase.AWAITING_MOVE -> PHASE_MOVE
        Phase.FINISHED -> PHASE_FINISHED
    }

    private fun stepsOf(event: PieceMoved) = if (event.fromProgress < 0) 1 else event.toProgress - event.fromProgress

    private companion object {
        const val AFTER_ROLL_MS = 2_100L
        const val PER_STEP_MS = 230L
        const val AFTER_MOVE_MS = 500L
        const val AFTER_CAPTURE_MS = 800L
        const val PHASE_ROLL = "ROLL"
        const val PHASE_MOVE = "MOVE"
        const val PHASE_FINISHED = "FINISHED"
        const val PIECE_ID = "pieceId"
        const val ACTION = "action"
        const val TYPE = "type"
    }
}

/** The session's bot and the trace it writes to. */
class RoyalLudoSessionBots(
    val bot: RoyalLudoBot,
    val trace: BotTrace,
) {
    constructor() : this(BotTrace())

    private constructor(trace: BotTrace) : this(RoyalLudoBot(trace = trace), trace)
}
