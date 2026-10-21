package com.kglabs28.yapnplay.backend.application.match.games.mono

import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.mono.MonoCardWire
import com.kglabs28.yapnplay.backend.contract.mono.MonoPublicState
import com.kglabs28.yapnplay.backend.contract.mono.MonoWireAction
import com.kglabs28.yapnplay.backend.contract.mono.MonoWireEvent
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoEvent
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoHouseRules
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoRules
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoBot
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoBotTuning
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoEngine
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Standard MONO inside a match. The engine owns no clock, so the session
 * keeps two: the MONO! window (stamped when the engine opens it; a missed
 * call draws the penalty) and the drawn-card window (a playable drawn card
 * must be played within it, or the turn passes). Seats the server plays
 * always call MONO!. Each viewer sees only their own hand, and only the
 * drawer sees which card was drawn.
 */
class MonoSession(
    private val engine: MonoEngine,
    private var state: MonoState,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    private val timing: MonoTiming = MonoTiming(),
    private var drawnAt: Long = 0L,
    private val bot: MonoBot = MonoBot(),
    private val trace: BotTrace = BotTrace(),
) : GameSession {
    /** Colours each seat recently failed to play (they drew instead): bot knowledge, not saved. */
    private val misses = mutableMapOf<Int, MutableSet<Color>>()

    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == MonoPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) engine.outcome(state).ranking.map { it.seat.value } else emptyList()
    override val turnSeconds: Int get() = timing.turnSeconds
    override val thinkMillis: Long get() = timing.thinkMillis
    override val waitsForTick: Boolean = false
    override val stillIn: Set<Int> get() = state.seats.map { it.value }.toSet()
    override val botTrace: List<BotTraceEntry> get() = trace.recent()
    override val canEndEarly: Boolean get() = !isFinished

    override fun colourOf(seat: Int): Colour = Colour.entries[(seat - 1) % Colour.entries.size]

    override fun decode(type: String, payload: JsonObject): Action? {
        if (type != MessageType.GAME_ACTION) return null
        val wire = payload[ACTION]?.jsonObject?.let { runCatching { json.decodeFromJsonElement(MonoWireAction.serializer(), it) }.getOrNull() }
        return when (wire) {
            is MonoWireAction.PlayCard -> MonoAction.PlayCard(wire.handIndex)
            MonoWireAction.DrawCard -> MonoAction.DrawCard
            is MonoWireAction.ChooseColor -> colourOf(wire.color)?.let { MonoAction.ChooseColor(it) }
            MonoWireAction.CallMono -> MonoAction.CallMono
            MonoWireAction.AcceptStack -> MonoAction.AcceptStack
            MonoWireAction.Pass -> MonoAction.Pass
            is MonoWireAction.ChooseSwapTarget -> MonoAction.ChooseSwapTarget(wire.seat)
            null -> null
        }
    }

    /** MONO! is called just after playing, when the turn has already moved on. */
    override fun actsOutOfTurn(seat: Int, action: Action): Boolean = action == MonoAction.CallMono

    override fun apply(seat: Int, action: Action): List<GameEvent>? = take(engine.apply(state, SeatNumber(seat), action, random))

    /** The server plays [seat]'s step (any mode: CPU, Autoplay, away, or a timed-out turn), and calls MONO! for it. */
    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? {
        val action = bot.choose(state, seat, misses) ?: return take(engine.autoplay(state, SeatNumber(seat), random))
        val events = take(engine.apply(state, SeatNumber(seat), action, random))
            ?: take(engine.autoplay(state, SeatNumber(seat), random))
            ?: return null
        return events + callMonoFor(seat)
    }

    /** The MONO! window (a seat the server plays calls at once; anyone else is penalised when it shuts) and the drawn-card window. */
    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? {
        val windowSeat = state.monoWindowSeat
        if (windowSeat != null && state.monoCallWindowExpiry > 0) {
            if (windowSeat in cpuSeats) return callMonoFor(windowSeat).ifEmpty { null }
            if (now >= state.monoCallWindowExpiry) return take(engine.apply(state, SeatNumber(windowSeat), MonoAction.ExpireMonoCall, random))
        }
        if (state.drawnCardIndex != null && drawnAt > 0 && now >= drawnAt + timing.drawPlayWindowMs && state.currentSeat !in cpuSeats) {
            return take(engine.apply(state, SeatNumber(state.currentSeat), MonoAction.Pass, random))
        }
        return null
    }

    override fun endEarly(): List<GameEvent>? = take(engine.apply(state, SeatNumber(state.currentSeat), MonoAction.EndMatch, random))

    override fun pauseAfter(events: List<GameEvent>): Long = events.sumOf { event ->
        when (event) {
            is MonoEvent.CardPlayed -> PLAY_MS
            is MonoEvent.CardDrawn -> DRAW_MS
            is MonoEvent.HandSwapped, is MonoEvent.HandsPassed -> SWAP_MS
            else -> 0L
        }
    }.coerceAtMost(MAX_PAUSE_MS)

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as MonoState
    }

    override fun publicState(viewerSeat: Int?): JsonObject {
        val seats = state.seats.map { it.value }
        return json.encodeToJsonElement(
            MonoPublicState.serializer(),
            MonoPublicState(
                phase = state.phase.name,
                topCard = state.discardPile.lastOrNull()?.toWire(),
                topCardColor = state.topCardColor?.name,
                hands = viewerSeat?.takeIf { it in seats }?.let { mapOf(it to state.handForSeat(it).map { c -> c.toWire() }) }.orEmpty(),
                cardCounts = seats.associateWith { state.cardCountForSeat(it) },
                drawPileCount = state.drawPile.size,
                discardPileCount = state.discardPile.size,
                activeStackPenalty = state.activeStackPenalty,
                stackTopValue = state.stackTopValue,
                monoCallWindowExpiry = state.monoCallWindowExpiry.coerceAtLeast(0),
                monoWindowSeat = state.monoWindowSeat,
                monoCalledSeat = state.monoCalledSeat,
                reversed = state.reversed,
                drawnCardIndex = state.drawnCardIndex,
                drawnCardExpiry = if (state.drawnCardIndex != null && drawnAt > 0) drawnAt + timing.drawPlayWindowMs else 0,
                pendingSwapSeat = state.pendingSwapSeat,
                eliminatedSeats = state.eliminatedSeats.toList(),
                finishedSeats = seats.filter { state.cardCountForSeat(it) == 0 },
                ranking = ranking,
            ),
        ).jsonObject
    }

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> = emptyList()

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = wireEventsFor(events, null)

    /** Only the drawer sees the card they drew; everyone else learns how many. */
    override fun wireEventsFor(events: List<GameEvent>, viewerSeat: Int?): List<JsonObject> {
        val out = mutableListOf<MonoWireEvent>()
        events.forEach { event ->
            val wire: MonoWireEvent? = when (event) {
                is MonoEvent.CardDrawn -> if (event.seat.value == viewerSeat) {
                    MonoWireEvent.CardDrawn(event.seat.value, event.card.toWire())
                } else {
                    val last = out.lastOrNull()
                    if (last is MonoWireEvent.CardsDrawn && last.seat == event.seat.value) {
                        out[out.lastIndex] = last.copy(count = last.count + 1)
                        null
                    } else {
                        MonoWireEvent.CardsDrawn(event.seat.value, 1)
                    }
                }
                is MonoEvent.CardPlayed -> MonoWireEvent.CardPlayed(event.seat.value, event.card.toWire(), event.color?.name)
                is MonoEvent.ColorChosen -> MonoWireEvent.ColorChosen(event.seat.value, event.color.name)
                is MonoEvent.MonoCalled -> MonoWireEvent.MonoCalled(event.seat.value)
                is MonoEvent.MonoMissed -> MonoWireEvent.MonoMissed(event.seat.value, event.penaltyCards)
                is MonoEvent.HandSwapped -> MonoWireEvent.HandSwapped(event.from.value, event.to.value)
                is MonoEvent.HandsPassed -> MonoWireEvent.HandsPassed(event.direction)
                is MonoEvent.TurnPassed -> MonoWireEvent.TurnPassed(event.seat.value)
                is MonoEvent.SeatFinished -> MonoWireEvent.SeatFinished(event.seat.value, event.place)
                is MonoEvent.MatchEnded -> MonoWireEvent.MatchEnded(event.finishingOrder)
                else -> null
            }
            wire?.let { out += it }
        }
        return out.map { json.encodeToJsonElement(MonoWireEvent.serializer(), it).jsonObject }
    }

    override fun save(): JsonObject = json.encodeToJsonElement(MonoSaved.serializer(), MonoSaved(state, drawnAt)).jsonObject

    /** Applies an engine result: stamps the MONO! window, starts or clears the drawn-card clock, and notes colour misses. */
    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        val before = state
        val now = clock.nowMillis()
        var next = result.state as MonoState
        if (next.monoWindowSeat != null && next.monoCallWindowExpiry == WINDOW_OPEN) next = next.copy(monoCallWindowExpiry = now + timing.monoWindowMs)
        drawnAt = when {
            next.drawnCardIndex == null -> 0L
            before.drawnCardIndex == null -> now
            else -> drawnAt
        }
        result.events.forEach { event ->
            when (event) {
                is MonoEvent.CardDrawn -> before.topCardColor?.let { if (event.seat.value == before.currentSeat) misses.getOrPut(event.seat.value) { mutableSetOf() } += it }
                is MonoEvent.CardPlayed -> event.card.playColor()?.let { misses[event.seat.value]?.remove(it) }
                else -> Unit
            }
        }
        state = next
        return result.events
    }

    private fun callMonoFor(seat: Int): List<GameEvent> =
        if (state.monoWindowSeat == seat && state.monoCallWindowExpiry != 0L) take(engine.apply(state, SeatNumber(seat), MonoAction.CallMono, random)).orEmpty() else emptyList()

    private fun colourOf(name: String): Color? = Color.entries.firstOrNull { it.name == name }

    private companion object {
        const val ACTION = "action"
        const val WINDOW_OPEN = -1L
        const val PLAY_MS = 700L
        const val DRAW_MS = 250L
        const val SWAP_MS = 1_200L
        const val MAX_PAUSE_MS = 3_000L
    }
}

/** A Standard MONO card as the wire carries it. */
fun MonoCard.toWire(): MonoCardWire = when (this) {
    is MonoCard.NumberCard -> MonoCardWire("NumberCard", color.name, number)
    is MonoCard.ReverseCard -> MonoCardWire("ReverseCard", color.name)
    is MonoCard.SkipCard -> MonoCardWire("SkipCard", color.name)
    is MonoCard.DrawTwoCard -> MonoCardWire("DrawTwoCard", color.name)
    MonoCard.PrismColorWild -> MonoCardWire("PrismColorWild")
    MonoCard.PrismDrawFour -> MonoCardWire("PrismDrawFour")
}

/** Turn time, CPU think time, the MONO! window and the drawn-card window (runtime settings). */
data class MonoTiming(
    val turnSeconds: Int = GameSettings.MONO_TURN_SECONDS.default,
    val thinkMillis: Long = GameSettings.MONO_CPU_THINK_MS.default.toLong(),
    val monoWindowMs: Long = GameSettings.MONO_CALL_WINDOW_MS.default.toLong(),
    val drawPlayWindowMs: Long = GameSettings.MONO_DRAW_PLAY_WINDOW_MS.default.toLong(),
) {
    companion object {
        fun from(settings: Settings) = MonoTiming(
            settings[GameSettings.MONO_TURN_SECONDS],
            settings[GameSettings.MONO_CPU_THINK_MS].toLong(),
            settings[GameSettings.MONO_CALL_WINDOW_MS].toLong(),
            settings[GameSettings.MONO_DRAW_PLAY_WINDOW_MS].toLong(),
        )

        fun botTuning(settings: Settings) = MonoBotTuning(
            cardCost = settings[GameSettings.MONO_BOT_CARD_COST],
            shedPct = settings[GameSettings.MONO_BOT_SHED_PCT],
            attackValue = settings[GameSettings.MONO_BOT_ATTACK_VALUE],
            holdValue = settings[GameSettings.MONO_BOT_HOLD_VALUE],
            colourValue = settings[GameSettings.MONO_BOT_COLOUR_VALUE],
            knockoutCost = settings[GameSettings.MONO_BOT_KNOCKOUT_COST],
        )
    }
}

@Serializable
private data class MonoSaved(val state: MonoState, val drawnAt: Long = 0)

/** Starts Standard MONO sessions; a room's toggles switch the house rules (stacking, 7 swap, 0 pass). */
class MonoSessionFactory(
    override val game: MonoEngine,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    private val settings: Settings = Settings.Defaults,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val toggles = houseRules.orEmpty()
        fun on(key: String, default: Boolean) = toggles[key] ?: default
        val base = MonoRules.TRADITIONAL
        val rules = base.copy(
            mandatory = base.mandatory.copy(monoPenaltyCards = settings[GameSettings.MONO_PENALTY_CARDS]),
            house = MonoHouseRules(
                stackingAllowed = on(STACKING, base.house.stackingAllowed),
                sevenSwap = on(SEVEN_SWAP, base.house.sevenSwap),
                zeroPass = on(ZERO_PASS, base.house.zeroPass),
            ),
        )
        return session(game.start(rules, seatCount, random) as MonoState, 0L)
    }

    override fun restore(saved: JsonObject): GameSession {
        val s = json.decodeFromJsonElement(MonoSaved.serializer(), saved)
        // Anyone mid-window gets a fresh one: nobody could act while the server was down.
        val now = clock.nowMillis()
        val state = if (s.state.monoCallWindowExpiry > 0) s.state.copy(monoCallWindowExpiry = now + MonoTiming.from(settings).monoWindowMs) else s.state
        return session(state, if (s.drawnAt > 0) now else 0L)
    }

    private fun session(state: MonoState, drawnAt: Long): MonoSession {
        val trace = BotTrace()
        return MonoSession(game, state, random, json, clock, MonoTiming.from(settings), drawnAt, MonoBot(MonoTiming.botTuning(settings), trace), trace)
    }

    private companion object {
        const val STACKING = "stacking_allowed"
        const val SEVEN_SWAP = "seven_swap"
        const val ZERO_PASS = "zero_pass"
    }
}
