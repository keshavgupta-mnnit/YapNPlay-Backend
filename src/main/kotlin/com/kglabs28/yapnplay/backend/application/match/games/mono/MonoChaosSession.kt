package com.kglabs28.yapnplay.backend.application.match.games.mono

import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosCardWire
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosPublicState
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosWireAction
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosWireEvent
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
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosEvent
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosHouseRules
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosBot
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosEngine
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * MONO: Complete Chaos inside a match: the same clocks as [MonoSession]
 * (MONO! window, drawn-card window), stacking, the Roulette and the
 * knockout. Seats the server plays always call MONO!. Each viewer sees only
 * their own hand.
 */
class MonoChaosSession(
    private val engine: MonoChaosEngine,
    private var state: MonoChaosState,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    private val timing: MonoTiming = MonoTiming(),
    private var drawnAt: Long = 0L,
    private val bot: MonoChaosBot = MonoChaosBot(),
    private val trace: BotTrace = BotTrace(),
) : GameSession {
    private val misses = mutableMapOf<Int, MutableSet<Color>>()

    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == MonoChaosPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) engine.outcome(state).ranking.map { it.seat.value } else emptyList()
    override val turnSeconds: Int get() = timing.turnSeconds
    override val thinkMillis: Long get() = timing.thinkMillis
    override val waitsForTick: Boolean = false
    override val stillIn: Set<Int> get() = state.seats.map { it.value }.filter { !state.isEliminated(it) }.toSet()
    override val botTrace: List<BotTraceEntry> get() = trace.recent()
    override val canEndEarly: Boolean get() = !isFinished

    override fun colourOf(seat: Int): Colour = Colour.entries[(seat - 1) % Colour.entries.size]

    override fun decode(type: String, payload: JsonObject): Action? {
        if (type != MessageType.GAME_ACTION) return null
        val wire = payload[ACTION]?.jsonObject?.let { runCatching { json.decodeFromJsonElement(MonoChaosWireAction.serializer(), it) }.getOrNull() }
        return when (wire) {
            is MonoChaosWireAction.PlayCard -> MonoChaosAction.PlayCard(wire.handIndex)
            MonoChaosWireAction.DrawUntilPlayable -> MonoChaosAction.DrawUntilPlayable
            is MonoChaosWireAction.ChooseColor -> colourOf(wire.color)?.let { MonoChaosAction.ChooseColor(it) }
            is MonoChaosWireAction.ChooseRouletteColor -> colourOf(wire.color)?.let { MonoChaosAction.ChooseRouletteColor(it) }
            MonoChaosWireAction.CallMono -> MonoChaosAction.CallMono
            is MonoChaosWireAction.StackDraw -> MonoChaosAction.StackDraw(wire.handIndex)
            MonoChaosWireAction.AcceptStack -> MonoChaosAction.AcceptStack
            MonoChaosWireAction.Pass -> MonoChaosAction.Pass
            is MonoChaosWireAction.ChooseSwapTarget -> MonoChaosAction.ChooseSwapTarget(wire.seat)
            null -> null
        }
    }

    /** MONO! is called just after playing, when the turn has already moved on. */
    override fun actsOutOfTurn(seat: Int, action: Action): Boolean = action == MonoChaosAction.CallMono

    override fun apply(seat: Int, action: Action): List<GameEvent>? = take(engine.apply(state, SeatNumber(seat), action, random))

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? {
        val action = bot.choose(state, seat, misses) ?: return take(engine.autoplay(state, SeatNumber(seat), random))
        val events = take(engine.apply(state, SeatNumber(seat), action, random))
            ?: take(engine.autoplay(state, SeatNumber(seat), random))
            ?: return null
        return events + callMonoFor(seat)
    }

    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? {
        val windowSeat = state.monoWindowSeat
        if (windowSeat != null && state.monoCallWindowExpiry > 0) {
            if (windowSeat in cpuSeats) return callMonoFor(windowSeat).ifEmpty { null }
            if (now >= state.monoCallWindowExpiry) return take(engine.apply(state, SeatNumber(windowSeat), MonoChaosAction.ExpireMonoCall, random))
        }
        if (state.drawnCardIndex != null && drawnAt > 0 && now >= drawnAt + timing.drawPlayWindowMs && state.currentSeat !in cpuSeats) {
            return take(engine.apply(state, SeatNumber(state.currentSeat), MonoChaosAction.Pass, random))
        }
        return null
    }

    override fun endEarly(): List<GameEvent>? = take(engine.apply(state, SeatNumber(state.currentSeat), MonoChaosAction.EndMatch, random))

    override fun pauseAfter(events: List<GameEvent>): Long = events.sumOf { event ->
        when (event) {
            is MonoChaosEvent.CardPlayed, is MonoChaosEvent.StackDraw -> PLAY_MS
            is MonoChaosEvent.CardsDrawn -> DRAW_MS * event.count
            is MonoChaosEvent.StackAccepted -> DRAW_MS * event.penaltyCards
            is MonoChaosEvent.HandSwapped, is MonoChaosEvent.HandsPassed, is MonoChaosEvent.PlayerEliminated -> SWAP_MS
            else -> 0L
        }
    }.coerceAtMost(MAX_PAUSE_MS)

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as MonoChaosState
    }

    override fun publicState(viewerSeat: Int?): JsonObject {
        val seats = state.seats.map { it.value }
        return json.encodeToJsonElement(
            MonoChaosPublicState.serializer(),
            MonoChaosPublicState(
                phase = state.phase.name,
                topCard = state.discardPile.lastOrNull()?.toWire(),
                topCardColor = state.topCardColor?.name,
                hands = viewerSeat?.takeIf { it in seats }?.let { mapOf(it to state.handForSeat(it).map { c -> c.toWire() }) }.orEmpty(),
                cardCounts = seats.associateWith { state.cardCountForSeat(it) },
                drawPileCount = state.drawPile.size,
                discardPileCount = state.discardPile.size,
                activeStackPenalty = state.activeStackPenalty,
                monoCallWindowExpiry = state.monoCallWindowExpiry.coerceAtLeast(0),
                monoWindowSeat = state.monoWindowSeat,
                monoCalledSeat = state.monoCalledSeat,
                stackTopValue = state.stackTopValue,
                reversed = state.reversed,
                drawnCardIndex = state.drawnCardIndex,
                pendingSwapSeat = state.pendingSwapSeat,
                eliminatedSeats = state.eliminatedSeats.toList(),
                finishedSeats = seats.filter { state.cardCountForSeat(it) == 0 && !state.isEliminated(it) },
                ranking = ranking,
                knockoutThreshold = state.monoChaosRules.mandatory.knockoutThreshold,
            ),
        ).jsonObject
    }

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> = emptyList()

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = wireEventsFor(events, null)

    override fun wireEventsFor(events: List<GameEvent>, viewerSeat: Int?): List<JsonObject> = events.mapNotNull { event ->
        when (event) {
            is MonoChaosEvent.CardPlayed -> MonoChaosWireEvent.CardPlayed(event.seat.value, event.card.toWire(), event.color?.name)
            is MonoChaosEvent.CardDrawn -> if (event.seat.value == viewerSeat) {
                MonoChaosWireEvent.CardDrawn(event.seat.value, event.card.toWire())
            } else {
                MonoChaosWireEvent.CardsDrawn(event.seat.value, 1)
            }
            is MonoChaosEvent.CardsDrawn -> MonoChaosWireEvent.CardsDrawn(event.seat.value, event.count)
            is MonoChaosEvent.CardsDiscarded -> MonoChaosWireEvent.CardsDiscarded(event.seat.value, event.count)
            is MonoChaosEvent.ColorChosen -> MonoChaosWireEvent.ColorChosen(event.seat.value, event.color.name)
            is MonoChaosEvent.RouletteColorChosen -> MonoChaosWireEvent.RouletteColorChosen(event.victim.value, event.color.name)
            is MonoChaosEvent.StackDraw -> MonoChaosWireEvent.StackDraw(event.seat.value, event.card.toWire(), event.newStackTotal)
            is MonoChaosEvent.StackAccepted -> MonoChaosWireEvent.StackAccepted(event.seat.value, event.penaltyCards)
            is MonoChaosEvent.MonoCalled -> MonoChaosWireEvent.MonoCalled(event.seat.value)
            is MonoChaosEvent.MonoMissed -> MonoChaosWireEvent.MonoMissed(event.seat.value, event.penaltyCards)
            is MonoChaosEvent.HandSwapped -> MonoChaosWireEvent.HandSwapped(event.from.value, event.to.value)
            is MonoChaosEvent.HandsPassed -> MonoChaosWireEvent.HandsPassed(event.direction)
            is MonoChaosEvent.PlayerEliminated -> MonoChaosWireEvent.PlayerEliminated(event.seat.value)
            is MonoChaosEvent.TurnPassed -> MonoChaosWireEvent.TurnPassed(event.seat.value)
            is MonoChaosEvent.SeatFinished -> MonoChaosWireEvent.SeatFinished(event.seat.value, event.place)
            is MonoChaosEvent.MatchEnded -> MonoChaosWireEvent.MatchEnded(event.finishingOrder)
            else -> null
        }?.let { json.encodeToJsonElement(MonoChaosWireEvent.serializer(), it).jsonObject }
    }

    override fun save(): JsonObject = json.encodeToJsonElement(MonoChaosSaved.serializer(), MonoChaosSaved(state, drawnAt)).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        val before = state
        val now = clock.nowMillis()
        var next = result.state as MonoChaosState
        if (next.monoWindowSeat != null && next.monoCallWindowExpiry == WINDOW_OPEN) next = next.copy(monoCallWindowExpiry = now + timing.monoWindowMs)
        drawnAt = when {
            next.drawnCardIndex == null -> 0L
            before.drawnCardIndex == null -> now
            else -> drawnAt
        }
        result.events.forEach { event ->
            when (event) {
                is MonoChaosEvent.CardsDrawn -> before.topCardColor?.let { if (event.seat.value == before.currentSeat) misses.getOrPut(event.seat.value) { mutableSetOf() } += it }
                is MonoChaosEvent.CardPlayed -> event.card.playColor()?.let { misses[event.seat.value]?.remove(it) }
                else -> Unit
            }
        }
        state = next
        return result.events
    }

    private fun callMonoFor(seat: Int): List<GameEvent> =
        if (state.monoWindowSeat == seat && state.monoCallWindowExpiry != 0L) take(engine.apply(state, SeatNumber(seat), MonoChaosAction.CallMono, random)).orEmpty() else emptyList()

    private fun colourOf(name: String): Color? = Color.entries.firstOrNull { it.name == name }

    private companion object {
        const val ACTION = "action"
        const val WINDOW_OPEN = -1L
        const val PLAY_MS = 700L
        const val DRAW_MS = 250L
        const val SWAP_MS = 1_200L
        const val MAX_PAUSE_MS = 4_000L
    }
}

/** A MONO: Complete Chaos card as the wire carries it. */
fun MonoChaosCard.toWire(): MonoChaosCardWire = when (this) {
    is MonoChaosCard.NumberCard -> MonoChaosCardWire("NumberCard", color.name, number)
    is MonoChaosCard.ReverseCard -> MonoChaosCardWire("ReverseCard", color.name)
    is MonoChaosCard.SkipCard -> MonoChaosCardWire("SkipCard", color.name)
    is MonoChaosCard.DrawTwoCard -> MonoChaosCardWire("DrawTwoCard", color.name)
    is MonoChaosCard.ColoredDrawFourCard -> MonoChaosCardWire("ColoredDrawFourCard", color.name)
    is MonoChaosCard.DiscardAllCard -> MonoChaosCardWire("DiscardAllCard", color.name)
    is MonoChaosCard.SkipEveryoneCard -> MonoChaosCardWire("SkipEveryoneCard", color.name)
    MonoChaosCard.PrismColorWild -> MonoChaosCardWire("PrismColorWild")
    MonoChaosCard.PrismDrawFour -> MonoChaosCardWire("PrismDrawFour")
    MonoChaosCard.PrismReverseDrawFour -> MonoChaosCardWire("PrismReverseDrawFour")
    MonoChaosCard.PrismDrawSix -> MonoChaosCardWire("PrismDrawSix")
    MonoChaosCard.PrismDrawTen -> MonoChaosCardWire("PrismDrawTen")
    MonoChaosCard.PrismColorRoulette -> MonoChaosCardWire("PrismColorRoulette")
}

@Serializable
private data class MonoChaosSaved(val state: MonoChaosState, val drawnAt: Long = 0)

/** Starts MONO: Complete Chaos sessions; a room's toggles switch the house rules. */
class MonoChaosSessionFactory(
    override val game: MonoChaosEngine,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    private val settings: Settings = Settings.Defaults,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val toggles = houseRules.orEmpty()
        fun on(key: String, default: Boolean) = toggles[key] ?: default
        val base = MonoChaosRules.TRADITIONAL
        val rules = base.copy(
            mandatory = base.mandatory.copy(
                monoPenaltyCards = settings[GameSettings.CHAOS_PENALTY_CARDS],
                knockoutThreshold = settings[GameSettings.CHAOS_KNOCKOUT_CARDS],
            ),
            house = MonoChaosHouseRules(
                stackingAllowed = on(STACKING, base.house.stackingAllowed),
                sevenSwap = on(SEVEN_SWAP, base.house.sevenSwap),
                zeroPass = on(ZERO_PASS, base.house.zeroPass),
                knockoutEnabled = on(KNOCKOUT, base.house.knockoutEnabled),
            ),
        )
        return session(game.start(rules, seatCount, random) as MonoChaosState, 0L)
    }

    override fun restore(saved: JsonObject): GameSession {
        val s = json.decodeFromJsonElement(MonoChaosSaved.serializer(), saved)
        val now = clock.nowMillis()
        val state = if (s.state.monoCallWindowExpiry > 0) s.state.copy(monoCallWindowExpiry = now + MonoTiming.from(settings).monoWindowMs) else s.state
        return session(state, if (s.drawnAt > 0) now else 0L)
    }

    private fun session(state: MonoChaosState, drawnAt: Long): MonoChaosSession {
        val trace = BotTrace()
        return MonoChaosSession(game, state, random, json, clock, MonoTiming.from(settings), drawnAt, MonoChaosBot(MonoTiming.botTuning(settings), trace), trace)
    }

    private companion object {
        const val STACKING = "stacking_allowed"
        const val SEVEN_SWAP = "seven_swap"
        const val ZERO_PASS = "zero_pass"
        const val KNOCKOUT = "knockout_enabled"
    }
}
