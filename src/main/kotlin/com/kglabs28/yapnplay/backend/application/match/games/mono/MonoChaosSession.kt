package com.kglabs28.yapnplay.backend.application.match.games.mono

import com.kglabs28.yapnplay.backend.application.match.games.mono.MonoDeckReshuffled
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
    private val vote: MonoContinueVote = MonoContinueVote(timing.voteMs),
    /** Finishers before the continue vote starts: 3 in rooms of 5+, else 2. */
    private val voteFrom: Int = MonoContinueVote.MIN_FINISHERS,
) : GameSession {
    private val misses = mutableMapOf<Int, MutableSet<Color>>()
    /** How long the table animates the last events (the continue vote's clock waits for it). */
    private var lastPauseMs = 0L
    /** When the current catch window opened (bots react from then). */
    private var catchOpenedAt = 0L

    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == MonoChaosPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) engine.outcome(state).ranking.map { it.seat.value } else emptyList()
    override val turnSeconds: Int get() = timing.turnSeconds
    /**
     * A bot's think time: longer for a colour, the Roulette, a stack or a swap
     * partner; none between the cards of a one-at-a-time draw (no choice in
     * it: the card's own pause paces it).
     */
    override val thinkMillis: Long get() = if (state.phase in DRAW_STEPS) 0L else timing.think.millis(state.phase in DECISIONS)
    /** The continue vote holds the table: no turn timer, no bot turns. */
    override val waitsForTick: Boolean get() = vote.active
    override val stillIn: Set<Int> get() = state.seats.map { it.value }.filter { !state.isEliminated(it) && it !in state.finishOrder }.toSet()
    override val botTrace: List<BotTraceEntry> get() = trace.recent()
    override val canEndEarly: Boolean get() = !isFinished && !vote.active

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
            MonoChaosWireAction.Caught -> MonoChaosAction.Caught
            is MonoChaosWireAction.VoteContinue -> MonoVoteContinue(wire.keepPlaying)
            null -> null
        }
    }

    /** MONO! is called just after playing, when the turn has already moved on. */
    override fun actsOutOfTurn(seat: Int, action: Action): Boolean = action == MonoChaosAction.CallMono || action == MonoChaosAction.Caught || action is MonoVoteContinue

    override fun apply(seat: Int, action: Action): List<GameEvent>? {
        if (action is MonoVoteContinue) return voteContinue(seat, action.keepPlaying)
        // While the table votes, nobody plays.
        if (vote.active) return null
        return take(engine.apply(state, SeatNumber(seat), action, random))
    }

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? {
        if (vote.active) return null
        // Two cards and about to play: call MONO! first (required under Call Before Play, allowed always).
        val early = if (state.handForSeat(seat).size == 2 && state.monoCalledSeat != seat) {
            take(engine.apply(state, SeatNumber(seat), MonoChaosAction.CallMono, random)).orEmpty()
        } else {
            emptyList()
        }
        val action = bot.choose(state, seat, misses) ?: return take(engine.autoplay(state, SeatNumber(seat), random))
        val events = take(engine.apply(state, SeatNumber(seat), action, random))
            ?: take(engine.autoplay(state, SeatNumber(seat), random))
            ?: return null
        return early + events + callMonoFor(seat)
    }

    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? {
        voteTick(now, botSeats)?.let { return it }
        if (vote.active) return null
        val target = state.catchSeat
        if (target != null) {
            // A seat the server plays calls at once if it can.
            if (target in cpuSeats && state.monoCalledSeat != target) callMonoFor(target).takeIf { it.isNotEmpty() }?.let { return it }
            // Bots catch a player without the shield after a human-like reaction time (never a false catch: the shield is visible).
            if (state.monoCalledSeat != target) {
                val catcher = cpuSeats.filter { it != target && it !in state.catchClosedFor && it in state.seats.map { s -> s.value } }
                    .sorted().firstOrNull { now >= catchOpenedAt + timing.catchReactionMs + it * CATCH_JITTER_MS }
                if (catcher != null) return take(engine.apply(state, SeatNumber(catcher), MonoChaosAction.Caught, random))
            }
        }
        return null
    }

    /** Opens a requested continue vote (the humans at the table vote; a table of bots just ends) and closes it in time. */
    private fun voteTick(now: Long, botSeats: Set<Int>): List<GameEvent>? = when {
        vote.pending -> {
            val voters = state.seats.map { it.value }.toSet() - botSeats
            if (voters.isEmpty()) {
                decideVote()
            } else {
                // The clock starts once the table has shown the finishing move.
                vote.open(now + lastPauseMs, voters)
                emptyList()
            }
        }
        vote.isOpen && now >= vote.endsAt -> decideVote()
        else -> null
    }

    private fun voteContinue(seat: Int, keepPlaying: Boolean): List<GameEvent>? {
        if (!vote.vote(seat, keepPlaying)) return null
        return if (vote.allVoted) decideVote() else emptyList()
    }

    /** Keep playing, or end now (ranked as at any end). */
    private fun decideVote(): List<GameEvent> {
        val result = vote.close()
        if (result.continued) return listOf(result)
        return listOf<GameEvent>(result) + take(engine.apply(state, SeatNumber(state.currentSeat), MonoChaosAction.EndMatch, random)).orEmpty()
    }

    /** A saved match was mid-vote: a fresh vote opens on the next tick. */
    fun resumeVote() = vote.request()

    /** One MONO turn is one clock: drawing, choosing a colour or a swap partner keeps the time left. A vote's end restarts it. */
    /**
     * A fresh full turn after a continue vote, and after the one draw a turn
     * allows when the drawn card can be played (play it or pass, in a whole
     * turn's time; the turn timer passes for them at the end).
     */
    override fun restartsClockOn(events: List<GameEvent>): Boolean =
        events.any { it is MonoContinueVoted } || (state.drawnCardIndex != null && events.any { it is MonoChaosEvent.CardDrawn })

    override fun endEarly(): List<GameEvent>? = take(engine.apply(state, SeatNumber(state.currentSeat), MonoChaosAction.EndMatch, random))

    override fun pauseAfter(events: List<GameEvent>): Long = events.sumOf { event ->
        when (event) {
            is MonoChaosEvent.CardPlayed, is MonoChaosEvent.StackDraw -> PLAY_MS
            // Drawing until a card fits, and the Roulette, come one card per step: each is seen before the next.
            is MonoChaosEvent.CardDrawn -> STEP_MS
            is MonoChaosEvent.CardsDiscarded -> DRAW_MS * event.count
            is MonoChaosEvent.StackAccepted -> DRAW_MS * event.penaltyCards
            is MonoChaosEvent.HandSwapped, is MonoChaosEvent.HandsPassed, is MonoChaosEvent.PlayerEliminated -> SWAP_MS
            is MonoChaosEvent.ColorChosen, is MonoChaosEvent.RouletteColorChosen, is MonoChaosEvent.MonoCaught, is MonoChaosEvent.FalseCatch -> NOTICE_MS
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
                finishedSeats = state.finishOrder,
                ranking = ranking,
                continueVote = vote.wire(),
                knockoutThreshold = state.monoChaosRules.mandatory.knockoutThreshold,
                catchSeat = state.catchSeat,
                catchClosedFor = state.catchClosedFor.sorted(),
                caughtPenaltyCards = state.monoChaosRules.mandatory.monoPenaltyCards,
                falseCatchPenaltyCards = state.monoChaosRules.mandatory.falseCatchPenaltyCards,
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
            is MonoChaosEvent.MonoCaught -> MonoChaosWireEvent.MonoCaught(event.catcher.value, event.seat.value, event.penaltyCards)
            is MonoChaosEvent.FalseCatch -> MonoChaosWireEvent.FalseCatch(event.seat.value, event.target.value, event.penaltyCards)
            is MonoDeckReshuffled -> MonoChaosWireEvent.DeckReshuffled(event.drawPileCount)
            is MonoChaosEvent.HandSwapped -> MonoChaosWireEvent.HandSwapped(event.from.value, event.to.value)
            is MonoChaosEvent.HandsPassed -> MonoChaosWireEvent.HandsPassed(event.direction)
            is MonoChaosEvent.PlayerEliminated -> MonoChaosWireEvent.PlayerEliminated(event.seat.value)
            is MonoChaosEvent.TurnPassed -> MonoChaosWireEvent.TurnPassed(event.seat.value)
            is MonoChaosEvent.SeatFinished -> MonoChaosWireEvent.SeatFinished(event.seat.value, event.place)
            is MonoChaosEvent.MatchEnded -> MonoChaosWireEvent.MatchEnded(event.finishingOrder)
            is MonoContinueVoted -> MonoChaosWireEvent.ContinueVoted(event.continued, event.yes, event.voters)
            else -> null
        }?.let { json.encodeToJsonElement(MonoChaosWireEvent.serializer(), it).jsonObject }
    }

    override fun save(): JsonObject = json.encodeToJsonElement(MonoChaosSaved.serializer(), MonoChaosSaved(state, drawnAt, vote.active, voteFrom)).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        val before = state
        val now = clock.nowMillis()
        var next = result.state as MonoChaosState
        if (next.monoWindowSeat != null && next.monoCallWindowExpiry == WINDOW_OPEN) next = next.copy(monoCallWindowExpiry = now + timing.monoWindowMs)
        if (next.catchSeat != null && next.catchSeat != before.catchSeat) catchOpenedAt = now
        val reshuffled = before.discardPile.size > next.discardPile.size || next.discardPile.take(before.discardPile.size) != before.discardPile
        drawnAt = when {
            next.drawnCardIndex == null -> 0L
            before.drawnCardIndex == null -> now
            else -> drawnAt
        }
        result.events.forEach { event ->
            when (event) {
                // Drawing (not the Roulette's flips) says the drawer had nothing of the live colour.
                is MonoChaosEvent.CardDrawn -> before.topCardColor?.let {
                    if (event.seat.value == before.currentSeat && before.phase != MonoChaosPhase.AWAITING_ROULETTE_DRAW) misses.getOrPut(event.seat.value) { mutableSetOf() } += it
                }
                is MonoChaosEvent.CardPlayed -> event.card.playColor()?.let { misses[event.seat.value]?.remove(it) }
                else -> Unit
            }
        }
        state = next
        lastPauseMs = pauseAfter(result.events)
        // From the 2nd finisher on, with 2+ seats still holding cards, the table votes to go on.
        if (next.phase != MonoChaosPhase.FINISHED && next.finishOrder.size >= voteFrom && result.events.any { it is MonoChaosEvent.SeatFinished }) vote.request()
        return if (reshuffled) result.events + MonoDeckReshuffled(next.drawPile.size) else result.events
    }

    /** MONO! for a seat the server plays, while its catch window is open (the old timed window is always 0 now). */
    private fun callMonoFor(seat: Int): List<GameEvent> =
        if (state.catchSeat == seat && state.monoCalledSeat != seat) take(engine.apply(state, SeatNumber(seat), MonoChaosAction.CallMono, random)).orEmpty() else emptyList()

    private fun colourOf(name: String): Color? = Color.entries.firstOrNull { it.name == name }

    private companion object {
        const val ACTION = "action"
        /** Each bot seat reacts a little later than the one before, as people do. */
        const val CATCH_JITTER_MS = 250L
        const val WINDOW_OPEN = -1L
        const val PLAY_MS = 700L
        const val DRAW_MS = 300L
        /** One card of a one-at-a-time draw. */
        const val STEP_MS = 800L
        const val SWAP_MS = 1_200L
        /** A colour change or a catch: long enough for the table's message to be read. */
        const val NOTICE_MS = 1_200L
        const val MAX_PAUSE_MS = 7_000L
        /** The phases where a seat draws one card after another. */
        val DRAW_STEPS = setOf(MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE, MonoChaosPhase.AWAITING_ROULETTE_DRAW)
        val DECISIONS = setOf(
            MonoChaosPhase.AWAITING_COLOR_CHOICE, MonoChaosPhase.AWAITING_ROULETTE_COLOR,
            MonoChaosPhase.AWAITING_STACK_CHOICE, MonoChaosPhase.AWAITING_SWAP_CHOICE,
        )
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
private data class MonoChaosSaved(val state: MonoChaosState, val drawnAt: Long = 0, val votePending: Boolean = false, val voteFrom: Int = MonoContinueVote.MIN_FINISHERS)

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
                falseCatchPenaltyCards = settings[GameSettings.CHAOS_FALSE_CATCH_CARDS],
                knockoutThreshold = settings[GameSettings.CHAOS_KNOCKOUT_CARDS],
            ),
            house = MonoChaosHouseRules(
                stackingAllowed = on(STACKING, base.house.stackingAllowed),
                sevenSwap = on(SEVEN_SWAP, base.house.sevenSwap),
                zeroPass = on(ZERO_PASS, base.house.zeroPass),
                knockoutEnabled = on(KNOCKOUT, base.house.knockoutEnabled),
            ),
        )
        return session(game.start(rules, seatCount, random) as MonoChaosState, 0L, MonoContinueVote.finishersBeforeVote(seatCount, houseRules != null))
    }

    override fun restore(saved: JsonObject): GameSession {
        val s = json.decodeFromJsonElement(MonoChaosSaved.serializer(), saved)
        val now = clock.nowMillis()
        val state = if (s.state.monoCallWindowExpiry > 0) s.state.copy(monoCallWindowExpiry = now + MonoTiming.from(settings).monoWindowMs) else s.state
        return session(state, if (s.drawnAt > 0) now else 0L, s.voteFrom).also { if (s.votePending) it.resumeVote() }
    }

    private fun session(state: MonoChaosState, drawnAt: Long, voteFrom: Int): MonoChaosSession {
        val trace = BotTrace()
        return MonoChaosSession(game, state, random, json, clock, MonoTiming.from(settings, chaos = true), drawnAt, MonoChaosBot(MonoTiming.botTuning(settings), trace), trace, voteFrom = voteFrom)
    }

    private companion object {
        const val STACKING = "stacking_allowed"
        const val SEVEN_SWAP = "seven_swap"
        const val ZERO_PASS = "zero_pass"
        const val KNOCKOUT = "knockout_enabled"
    }
}
