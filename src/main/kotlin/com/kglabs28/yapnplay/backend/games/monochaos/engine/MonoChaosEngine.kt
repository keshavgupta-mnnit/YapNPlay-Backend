package com.kglabs28.yapnplay.backend.games.monochaos.engine

import com.kglabs28.yapnplay.backend.games.base.Game
import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosEvent
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosTurnState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosView
import com.kglabs28.yapnplay.backend.games.monochaos.domain.buildMonoChaosDeck
import com.kglabs28.yapnplay.backend.games.monochaos.domain.canStackOnto
import com.kglabs28.yapnplay.backend.games.monochaos.domain.isPlayableOn
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants.Color

/**
 * MONO: Complete Chaos: match on colour, number or symbol; stack draw
 * cards (+2, +4, +6, +10) onto each other; a 7 swaps hands and a 0 passes
 * every hand; 25 cards in hand is a knockout; drawing runs until
 * something is playable. Pure: [random] drives every shuffle, and the
 * host stamps the MONO! deadline.
 */
class MonoChaosEngine : Game {
    override val id = GameId(MonoChaosConstants.ID)
    override val displayName = MonoChaosConstants.DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = MonoChaosConstants.TURN_SECONDS)
    override val matchmaking = MatchmakingConfig(
        seats = MonoChaosConstants.QUICK_PLAY_SEATS,
        searchSeconds = MonoChaosConstants.SEARCH_SECONDS,
    )
    override val showcase = GameShowcase(taglines = MonoChaosConstants.TAGLINES, artKey = MonoChaosConstants.ID)
    override val houseRuleOptions = MonoChaosConstants.HOUSE_RULES
    override val fixedRules = MonoChaosConstants.FIXED_RULES
    override val minPlayers = MonoChaosConstants.PLAYERS_MIN
    override val maxPlayers = MonoChaosConstants.PLAYERS_MAX
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Sequential
    override val bot = MonoChaosBot()

    override fun defaultRules(): GameRules = MonoChaosRules.TRADITIONAL

    override fun rulesSummary(): String = MonoChaosConstants.FIXED_RULES.joinToString(". ")

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        require(seatCount in MonoChaosConstants.PLAYERS_MIN..MonoChaosConstants.PLAYERS_MAX) {
            "MONO: Complete Chaos requires 2-8 players"
        }
        val chaosRules = rules as? MonoChaosRules ?: MonoChaosRules.TRADITIONAL
        val deck = buildMonoChaosDeck().shuffled(random)
        val seats = (1..seatCount).map { SeatNumber(it) }
        val handSize = chaosRules.mandatory.startingHandSize

        val hands = mutableMapOf<Int, List<MonoChaosCard>>()
        var cardIndex = 0
        for (seat in 1..seatCount) {
            hands[seat] = deck.subList(cardIndex, cardIndex + handSize)
            cardIndex += handSize
        }

        val drawPile = deck.subList(cardIndex, deck.size).toMutableList()

        // The starting card is always a number card (no action on the first player): keep flipping.
        while (drawPile[0] !is MonoChaosCard.NumberCard) drawPile.add(drawPile.removeAt(0))
        val face = drawPile.removeAt(0)

        val firstSeat = random.nextInt(seatCount) + 1
        return MonoChaosState(
            _gameId = id, _rules = chaosRules, _seats = seats,
            _turn = MonoChaosTurnState(SeatNumber(firstSeat), MonoChaosPhase.AWAITING_PLAY.name),
            hands = hands, drawPile = drawPile, discardPile = listOf(face),
            currentSeat = firstSeat,
            phase = MonoChaosPhase.AWAITING_PLAY,
            topCardColor = face.playColor(),
        )
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as MonoChaosState
        if (s.phase == MonoChaosPhase.FINISHED) return rejected("MATCH_ENDED", "Match ended")

        // The MONO! window can shut from outside a seat's turn.
        if (action is MonoChaosAction.ExpireMonoCall) return expireMonoCall(s, seat, random)
        // The server ends the match early (the players voted to stop).
        if (action is MonoChaosAction.EndMatch) return endMatch(s)
        // MONO! and Caught can come from any seat at any moment.
        if (action is MonoChaosAction.CallMono) return callMono(s, seat)
        if (action is MonoChaosAction.Caught) return caught(s, seat, random).let { if (it is ApplyResult.Success) settle(it.state as MonoChaosState, it.events) else it }

        if (s.isEliminated(seat.value)) return rejected("SEAT_ELIMINATED", "Seat eliminated")
        if (seat.value != s.currentSeat) return rejected("NOT_YOUR_TURN", "Not your turn")
        // Every move only in its own phase: a modified client can't draw or pass its way out of a penalty.
        if (!allowedNow(s, action)) return rejected("BAD_PHASE", "Not allowed now")

        val result = when (action) {
            is MonoChaosAction.PlayCard -> playCard(s, seat, action.handIndex, random)
            MonoChaosAction.DrawUntilPlayable -> if (s.phase == MonoChaosPhase.AWAITING_ROULETTE_DRAW) rouletteStep(s, seat, random) else drawUntilPlayable(s, seat, random)
            is MonoChaosAction.ChooseColor -> chooseColor(s, seat, action.color, random)
            is MonoChaosAction.ChooseRouletteColor -> chooseRouletteColor(s, seat, action.color, random)
            MonoChaosAction.CallMono -> callMono(s, seat)
            is MonoChaosAction.StackDraw -> stackDraw(s, seat, action.handIndex, random)
            MonoChaosAction.AcceptStack -> acceptStack(s, seat, random)
            is MonoChaosAction.ChooseSwapTarget -> chooseSwapTarget(s, seat, action.seat, random)
            MonoChaosAction.Pass -> passTurn(s, seat)
            MonoChaosAction.ExpireMonoCall, MonoChaosAction.EndMatch -> rejected("BAD_REQUEST", "Handled above")
            else -> rejected("BAD_REQUEST", "Unknown action for MONO: Complete Chaos")
        }
        return if (result is ApplyResult.Success) settle(catchWindow(s, result.state as MonoChaosState, seat.value), result.events) else result
    }

    /**
     * Play goes on after a seat empties its hand: it joins
     * [MonoChaosState.finishOrder] and is skipped from then on (it only names
     * the colour of a last wild). The match ends when one seat or none is
     * still in play (not finished, not knocked out).
     */
    private fun settle(after: MonoChaosState, events: List<GameEvent>): ApplyResult {
        var s = after
        val out = events.toMutableList()
        s.seats.map { it.value }.filter { it !in s.finishOrder && !s.isEliminated(it) && s.handForSeat(it).isEmpty() }.forEach { seat ->
            s = s.copy(finishOrder = s.finishOrder + seat)
            out += MonoChaosEvent.SeatFinished(SeatNumber(seat), s.finishOrder.size)
        }
        if (activeSeats(s).size <= 1) {
            val ended = s.copy(
                phase = MonoChaosPhase.FINISHED, catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet(), monoWindowSeat = null,
                turn = MonoChaosTurnState(SeatNumber(s.currentSeat), MonoChaosPhase.FINISHED.name),
            )
            return ApplyResult.Success(ended, out + MonoChaosEvent.MatchEnded(outcome(ended).ranking.map { it.seat.value }))
        }
        if (s.currentSeat !in activeSeats(s) && s.phase != MonoChaosPhase.AWAITING_COLOR_CHOICE) {
            val next = nextActive(s, s.currentSeat)
            val phase = if (s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE) s.phase else MonoChaosPhase.AWAITING_PLAY
            s = s.copy(currentSeat = next, phase = phase, drawnCardIndex = null, pendingSwapSeat = null, turn = MonoChaosTurnState(SeatNumber(next), phase.name))
        }
        return ApplyResult.Success(s, out)
    }

    /** The catch window, after every move (see the Standard engine): open for as long as the seat holds one card, whoever's turn it is. */
    private fun catchWindow(before: MonoChaosState, after: MonoChaosState, actor: Int): MonoChaosState {
        var s = after
        s.catchSeat?.let { target ->
            if (s.handForSeat(target).size != 1 || s.isEliminated(target)) s = s.copy(catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet())
        }
        if (before.handForSeat(actor).size > 1 && s.handForSeat(actor).size == 1) {
            s = s.copy(catchSeat = actor, catchNextSeat = null, catchClosedFor = emptySet(), monoCalledSeat = if (before.monoCalledSeat == actor) actor else null)
        }
        if (s.catchSeat != null && s.catchNextSeat == null && s.currentSeat != s.catchSeat) s = s.copy(catchNextSeat = s.currentSeat)
        if (s.monoCalledSeat != null && s.monoCalledSeat != s.catchSeat && s.currentSeat != s.monoCalledSeat) s = s.copy(monoCalledSeat = null)
        return s.copy(monoWindowSeat = s.catchSeat, monoCallWindowExpiry = 0)
    }

    /**
     * Caught: the player on one card hadn't called (they draw the penalty and
     * the window closes), or had (a wrong catch: the catcher draws, once per
     * window). Either draw can knock the drawer out.
     */
    private fun caught(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val target = s.catchSeat
        if (seat.value == target || seat.value !in s.seats.map { it.value } || s.isEliminated(seat.value) || seat.value in s.finishOrder) return rejected("BAD_TARGET", "You can't catch yourself")
        if (target != null && seat.value in s.catchClosedFor) return rejected("ALREADY_TRIED", "One try per player")
        // Pressing CAUGHT with nobody to catch, or on a player who called MONO, is a wrong catch: the presser pays.
        val falseCatch = target == null || s.monoCalledSeat == target
        val drawer = if (falseCatch) seat.value else target ?: seat.value
        val penalty = if (falseCatch) s.monoChaosRules.mandatory.falseCatchPenaltyCards else s.monoChaosRules.mandatory.monoPenaltyCards
        val events = mutableListOf<MonoChaosEvent>()
        val drawn = drawPenalty(s, drawer, penalty, random)
        var after = drawn.state
        after = if (falseCatch) {
            events += MonoChaosEvent.FalseCatch(seat, SeatNumber(target ?: seat.value), drawn.taken)
            if (target == null) after else after.copy(catchClosedFor = s.catchClosedFor + seat.value)
        } else {
            events += MonoChaosEvent.MonoCaught(seat, SeatNumber(drawer), drawn.taken)
            after.copy(catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet(), monoWindowSeat = null, monoCallWindowExpiry = 0)
        }
        after = knockOut(after, drawer, events)
        // A knockout here can leave the turn on a seat that is out, or one player standing.
        val next = if (after.isEliminated(after.currentSeat)) nextActive(after, after.currentSeat) else after.currentSeat
        return finishOrContinue(after.copy(currentSeat = next, turn = MonoChaosTurnState(SeatNumber(next), after.phase.name)), next, events)
    }

    /** Which moves the phase allows. */
    private fun allowedNow(s: MonoChaosState, action: Action): Boolean {
        val playing = s.phase == MonoChaosPhase.AWAITING_PLAY || s.phase == MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE
        return when (action) {
            is MonoChaosAction.PlayCard -> playing || s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE
            MonoChaosAction.DrawUntilPlayable -> (playing && s.drawnCardIndex == null) || s.phase == MonoChaosPhase.AWAITING_ROULETTE_DRAW
            MonoChaosAction.Pass -> playing && s.drawnCardIndex != null
            is MonoChaosAction.StackDraw, MonoChaosAction.AcceptStack -> s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE
            is MonoChaosAction.ChooseColor -> s.phase == MonoChaosPhase.AWAITING_COLOR_CHOICE
            is MonoChaosAction.ChooseRouletteColor -> s.phase == MonoChaosPhase.AWAITING_ROULETTE_COLOR
            is MonoChaosAction.ChooseSwapTarget -> s.phase == MonoChaosPhase.AWAITING_SWAP_CHOICE
            else -> true
        }
    }

    override fun currentActor(state: GameState) = SeatNumber((state as MonoChaosState).currentSeat)

    /**
     * Plays [seat]'s turn for them (a timeout, a bot's fallback). It ends the
     * way every move does — a seat that empties its hand finishes, and the
     * catch window moves on — exactly as through [apply].
     */
    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val s = state as MonoChaosState
        val result = autoMove(s, seat, random)
        return if (result is ApplyResult.Success) settle(catchWindow(s, result.state as MonoChaosState, seat.value), result.events) else result
    }

    /** The move [autoplay] makes, before the end-of-move bookkeeping. */
    private fun autoMove(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.phase == MonoChaosPhase.FINISHED) return rejected("MATCH_ENDED", "Match ended")
        if (s.isEliminated(seat.value)) return rejected("SEAT_ELIMINATED", "Seat eliminated")
        if (seat.value != s.currentSeat) return rejected("NOT_YOUR_TURN", "Not your turn")

        return when (s.phase) {
            MonoChaosPhase.AWAITING_PLAY -> {
                val allowed = s.drawnCardIndex?.let { listOf(it) }
                    ?: s.handForSeat(seat.value).indexesOf {
                        it.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)
                    }
                if (allowed.isNotEmpty()) {
                    playCard(s, seat, bestCard(s, seat.value, allowed), random)
                } else {
                    drawUntilPlayable(s, seat, random)
                }
            }
            MonoChaosPhase.AWAITING_COLOR_CHOICE -> chooseColor(s, seat, bestColour(s, seat.value), random)
            MonoChaosPhase.AWAITING_ROULETTE_COLOR -> chooseRouletteColor(s, seat, bestColour(s, seat.value), random)
            MonoChaosPhase.AWAITING_STACK_CHOICE -> {
                // Any draw card worth at least the last one stacks, whatever its colour (as in [stackDraw]).
                val stackable = s.handForSeat(seat.value).withIndex()
                    .filter { (_, card) -> card.canStackOnto(s.stackTopValue) }
                if (stackable.isNotEmpty()) {
                    stackDraw(s, seat, stackable.maxByOrNull { it.value.penaltyValue() }!!.index, random)
                } else {
                    acceptStack(s, seat, random)
                }
            }
            MonoChaosPhase.AWAITING_SWAP_CHOICE -> chooseSwapTarget(s, seat, swapPartner(s, seat.value), random)
            MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE -> drawUntilPlayable(s, seat, random)
            MonoChaosPhase.AWAITING_ROULETTE_DRAW -> rouletteStep(s, seat, random)
            MonoChaosPhase.FINISHED -> rejected("MATCH_ENDED", "Match ended")
        }
    }

    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as MonoChaosState
        if (seat.value !in s.seats.map { it.value }) return s
        val newSeats = s.seats.filter { it.value != seat.value }
        val newHands = s.hands - seat.value
        val remaining = newSeats.filter { !s.isEliminated(it.value) && it.value !in s.finishOrder }
        var newCurrentSeat = s.currentSeat
        if (seat.value == s.currentSeat) {
            newCurrentSeat = remaining.firstOrNull()?.value ?: newSeats.firstOrNull()?.value ?: s.currentSeat
        }
        val finished = remaining.size <= 1
        return s.copy(
            seats = newSeats,
            hands = newHands,
            currentSeat = newCurrentSeat,
            phase = if (finished) MonoChaosPhase.FINISHED else s.phase,
            turn = MonoChaosTurnState(
                SeatNumber(newCurrentSeat),
                if (finished) MonoChaosPhase.FINISHED.name else s.phase.name,
            ),
        )
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView {
        val s = state as MonoChaosState
        val ownHand = when (viewer) {
            is Viewer.Player -> s.handForSeat(viewer.seat.value)
            Viewer.Spectator -> emptyList()
        }
        return MonoChaosView(
            seats = s.seats.map { it.value },
            currentSeat = s.currentSeat,
            phase = s.phase.name,
            topCard = s.discardPile.lastOrNull(),
            topCardColor = s.topCardColor,
            hand = ownHand,
            opponentCardCounts = s.seats.associate { it.value to s.cardCountForSeat(it.value) },
            drawPileCount = s.drawPile.size,
            discardPileCount = s.discardPile.size,
            monoCallWindowExpiry = s.monoCallWindowExpiry,
            activeStackPenalty = s.activeStackPenalty,
            eliminatedSeats = s.eliminatedSeats,
            monoWindowSeat = s.monoWindowSeat,
            monoCalledSeat = s.monoCalledSeat,
            stackTopValue = s.stackTopValue,
            reversed = s.reversed,
            drawnCardIndex = s.drawnCardIndex,
            pendingSwapSeat = s.pendingSwapSeat,
        )
    }

    override fun isFinished(state: GameState) = (state as MonoChaosState).phase == MonoChaosPhase.FINISHED

    /**
     * Finished seats in the order they finished, then everyone still holding
     * cards by fewest cards (a tie: whoever plays sooner from the current
     * turn), then knocked-out seats (the latest knockout higher).
     */
    override fun outcome(state: GameState): MatchOutcome {
        val s = state as MonoChaosState
        val holding = s.seats.map { it.value }
            .filter { it !in s.finishOrder && !s.isEliminated(it) }
            .sortedWith(compareBy({ s.cardCountForSeat(it) }, { turnDistance(s, it) }))
        val ranking = (s.finishOrder + holding).mapIndexed { i, seat -> RankedSeat(SeatNumber(seat), SeatStatus.FINISHED, i + 1) }.toMutableList()
        s.eliminatedSeats.toList().asReversed().filter { it !in s.finishOrder }
            .forEach { ranking.add(RankedSeat(SeatNumber(it), SeatStatus.REMOVED, ranking.size + 1)) }
        return MatchOutcome(ranking)
    }

    /** Turns until [seat] plays, counted from the current seat in the direction of play. */
    private fun turnDistance(s: MonoChaosState, seat: Int): Int {
        val seats = s.seats.map { it.value }
        val n = seats.size
        val from = seats.indexOf(s.currentSeat).coerceAtLeast(0)
        val at = seats.indexOf(seat)
        return if (s.reversed) (from - at + n) % n else (at - from + n) % n
    }

    private fun playCard(s: MonoChaosState, seat: SeatNumber, handIndex: Int, random: GameRandom): ApplyResult {
        val hand = s.handForSeat(seat.value)
        if (handIndex !in hand.indices) return rejected("BAD_CARD", "No card at that position")
        // A penalty on the table can only be stacked onto or taken.
        if (s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE) {
            return stackDraw(s, seat, handIndex, random)
        }
        s.drawnCardIndex?.let { drawn ->
            if (handIndex != drawn) return rejected("MUST_PLAY_DRAWN", "After a draw you play the drawn card")
        }
        val card = hand[handIndex]
        if (!card.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)) {
            return rejected("NOT_PLAYABLE", "Card does not match")
        }

        val events = mutableListOf<MonoChaosEvent>()
        val newHand = hand.toMutableList().apply { removeAt(handIndex) }
        var played = s.copy(
            hands = s.hands + (seat.value to newHand),
            discardPile = s.discardPile + card,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = card,
            drawnCardIndex = null,
        )

        if (newHand.size == 1) {
            played = played.copy(monoWindowSeat = seat.value, monoCallWindowExpiry = WINDOW_OPEN, monoCalledSeat = null)
        } else if (s.monoWindowSeat == seat.value) {
            played = played.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
        }

        events += MonoChaosEvent.CardPlayed(seat, card, card.playColor())

        // A last card still takes effect (the seat finishes in [settle]); only a swap or pass is skipped.
        val lastCard = newHand.isEmpty()

        val victim = nextActive(played, seat.value)

        return when (card) {
            MonoChaosCard.PrismColorRoulette -> {
                // The victim names the colour they must draw to.
                val roulette = played.copy(
                    currentSeat = victim,
                    phase = MonoChaosPhase.AWAITING_ROULETTE_COLOR,
                    turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_ROULETTE_COLOR.name),
                )
                ApplyResult.Success(roulette, events)
            }

            is MonoChaosCard.PrismColorWild, is MonoChaosCard.PrismDrawFour,
            is MonoChaosCard.PrismReverseDrawFour, is MonoChaosCard.PrismDrawSix,
            is MonoChaosCard.PrismDrawTen -> ApplyResult.Success(
                played.copy(
                    phase = MonoChaosPhase.AWAITING_COLOR_CHOICE,
                    currentSeat = seat.value,
                    turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_COLOR_CHOICE.name),
                ),
                events,
            )

            is MonoChaosCard.DrawTwoCard, is MonoChaosCard.ColoredDrawFourCard ->
                ApplyResult.Success(openStack(played, victim, card.penaltyValue()), events)

            is MonoChaosCard.DiscardAllCard -> {
                val colour = card.color
                val gone = newHand.filter { it.playColor() == colour }
                var after = played.copy(
                    hands = played.hands + (seat.value to newHand.filter { it.playColor() != colour }),
                    discardPile = played.discardPile.dropLast(1) + gone + card,
                    topCardColor = colour,
                    phase = MonoChaosPhase.AWAITING_PLAY,
                    currentSeat = victim,
                    turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_PLAY.name),
                )
                if (gone.isNotEmpty()) events += MonoChaosEvent.CardsDiscarded(seat, gone.size)
                // Shedding the whole hand this way finishes too ([settle]).
                val left = after.cardCountForSeat(seat.value)
                after = when {
                    left == 1 -> after.copy(monoWindowSeat = seat.value, monoCallWindowExpiry = WINDOW_OPEN, monoCalledSeat = null)
                    after.monoWindowSeat == seat.value -> after.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
                    else -> after
                }
                finishOrContinue(after, victim, events)
            }

            is MonoChaosCard.SkipEveryoneCard -> {
                // Everyone else is skipped: the same seat plays again.
                val after = played.copy(
                    topCardColor = card.playColor(),
                    phase = MonoChaosPhase.AWAITING_PLAY,
                    currentSeat = seat.value,
                    turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_PLAY.name),
                )
                ApplyResult.Success(after, events)
            }

            is MonoChaosCard.SkipCard -> {
                val skipped = advance(played, seat.value, 2)
                val after = played.copy(
                    topCardColor = card.playColor(),
                    phase = MonoChaosPhase.AWAITING_PLAY,
                    currentSeat = skipped,
                    turn = MonoChaosTurnState(SeatNumber(skipped), MonoChaosPhase.AWAITING_PLAY.name),
                )
                ApplyResult.Success(after, events)
            }

            is MonoChaosCard.ReverseCard -> {
                val reversed = !played.reversed
                val flipped = played.copy(reversed = reversed)
                // Two players: Reverse acts as a Skip, so it comes straight back.
                val next = if (activeSeats(flipped).size <= 2) {
                    seat.value
                } else {
                    advance(flipped, seat.value, 1)
                }
                val after = flipped.copy(
                    topCardColor = card.playColor(),
                    phase = MonoChaosPhase.AWAITING_PLAY,
                    currentSeat = next,
                    turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
                )
                ApplyResult.Success(after, events)
            }

            is MonoChaosCard.NumberCard -> when {
                card.number == SEVEN && s.monoChaosRules.house.sevenSwap && !lastCard -> ApplyResult.Success(
                    played.copy(
                        phase = MonoChaosPhase.AWAITING_SWAP_CHOICE,
                        pendingSwapSeat = seat.value,
                        currentSeat = seat.value,
                        turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_SWAP_CHOICE.name),
                    ),
                    events,
                )
                card.number == ZERO && s.monoChaosRules.house.zeroPass && !lastCard -> {
                    var passed = passAllHands(played.copy(topCardColor = card.color))
                    events += MonoChaosEvent.HandsPassed(if (passed.reversed) COUNTER else CLOCKWISE)
                    // Anyone handed 25+ cards is out at once.
                    activeSeats(passed).forEach { passed = knockOut(passed, it, events) }
                    val victimAfter = if (passed.isEliminated(victim)) nextActive(passed, seat.value) else victim
                    val after = passed.copy(
                        phase = MonoChaosPhase.AWAITING_PLAY,
                        currentSeat = victimAfter,
                        turn = MonoChaosTurnState(SeatNumber(victimAfter), MonoChaosPhase.AWAITING_PLAY.name),
                    )
                    finishOrContinue(after, victimAfter, events)
                }
                else -> ApplyResult.Success(
                    played.copy(
                        topCardColor = card.color,
                        phase = MonoChaosPhase.AWAITING_PLAY,
                        currentSeat = victim,
                        turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_PLAY.name),
                    ),
                    events,
                )
            }

        }
    }

    /** The move's result; [settle] ends the match when one seat or none is left in play. */
    @Suppress("UNUSED_PARAMETER")
    private fun finishOrContinue(s: MonoChaosState, next: Int, events: MutableList<MonoChaosEvent>): ApplyResult = ApplyResult.Success(s, events)

    /** Puts [value] on the table for [victim], who may stack onto it. */
    private fun openStack(s: MonoChaosState, victim: Int, value: Int): MonoChaosState = s.copy(
        currentSeat = victim,
        phase = MonoChaosPhase.AWAITING_STACK_CHOICE,
        activeStackPenalty = value,
        stackTopValue = value,
        turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_STACK_CHOICE.name),
    )

    private fun chooseColor(s: MonoChaosState, seat: SeatNumber, color: Color, random: GameRandom): ApplyResult {
        if (s.phase != MonoChaosPhase.AWAITING_COLOR_CHOICE) return rejected("NOT_WAITING", "Not choosing a colour")
        val top = s.discardPile.lastOrNull()
        val reverses = top is MonoChaosCard.PrismReverseDrawFour
        val penalty = top?.penaltyValue() ?: 0

        var chosen = s.copy(
            topCardColor = color,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            phase = MonoChaosPhase.AWAITING_PLAY,
        )
        val events = mutableListOf<MonoChaosEvent>(MonoChaosEvent.ColorChosen(seat, color))

        if (reverses) chosen = chosen.copy(reversed = !chosen.reversed)
        // The next player in the new direction takes it: with two players, always the other one.
        val victim = nextActive(chosen, seat.value)

        if (penalty > 0) {
            // A stacked wild card carries the whole stack on; a fresh one opens it at its own value.
            val stacked = s.activeStackPenalty > 0
            val opened = openStack(chosen, victim, if (stacked) s.activeStackPenalty else penalty)
            return ApplyResult.Success(if (stacked) opened.copy(stackTopValue = penalty) else opened, events)
        }
        return ApplyResult.Success(
            chosen.copy(
                currentSeat = victim,
                turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_PLAY.name),
            ),
            events,
        )
    }

    /**
     * The Roulette: the victim names a colour, then flips cards from the deck
     * one at a time ([rouletteStep]) until that colour comes up, as at a real
     * table. Multi-colour cards never match.
     */
    private fun chooseRouletteColor(s: MonoChaosState, seat: SeatNumber, color: Color, random: GameRandom): ApplyResult {
        if (s.phase != MonoChaosPhase.AWAITING_ROULETTE_COLOR) {
            return rejected("NOT_WAITING", "Not choosing a roulette colour")
        }
        val flipping = s.copy(
            phase = MonoChaosPhase.AWAITING_ROULETTE_DRAW,
            rouletteColor = color,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_ROULETTE_DRAW.name),
        )
        return ApplyResult.Success(flipping, listOf<MonoChaosEvent>(MonoChaosEvent.RouletteColorChosen(seat, color)))
    }

    /**
     * One flip of the Roulette: the top card of the deck joins the victim's
     * hand. When it is the named colour (or the deck runs dry, or the victim
     * is knocked out) the colour becomes live and the victim's turn is over;
     * otherwise they flip again.
     */
    private fun rouletteStep(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val color = s.rouletteColor ?: return rejected("NOT_WAITING", "No roulette is on")
        val drawn = drawOne(s, random)
        val events = mutableListOf<MonoChaosEvent>()
        var after = drawn.state.copy(turnCounter = s.turnCounter + 1, lastActionSeat = seat.value)
        drawn.card?.let { card ->
            after = after.copy(hands = after.hands + (seat.value to after.handForSeat(seat.value) + card))
            events += MonoChaosEvent.CardDrawn(seat, card)
        }
        after = knockOut(after, seat.value, events)
        val done = drawn.card == null || drawn.card.playColor() == color || after.isEliminated(seat.value)
        if (!done) return ApplyResult.Success(after, events)
        val next = nextActive(after, seat.value)
        events += MonoChaosEvent.TurnPassed(seat)
        val ready = after.copy(
            topCardColor = color,
            rouletteColor = null,
            currentSeat = next,
            phase = MonoChaosPhase.AWAITING_PLAY,
            turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
        )
        return finishOrContinue(ready, next, events)
    }

    /** The deck's top card (the discard pile, all but its top, reshuffled in first when the deck is empty); null when none is left. */
    private fun drawOne(s: MonoChaosState, random: GameRandom): DrawnOne {
        var drawPile = s.drawPile
        var discard = s.discardPile
        if (drawPile.isEmpty() && discard.size > 1) {
            val face = discard.last()
            drawPile = discard.dropLast(1).shuffled(random)
            discard = listOf(face)
        }
        val card = drawPile.firstOrNull()
        return DrawnOne(s.copy(drawPile = drawPile.drop(if (card == null) 0 else 1), discardPile = discard), card)
    }

    /**
     * Draw until you can play, one card at a time as at a real table: each
     * step adds one card. If it can be played, the player plays it (or
     * passes); if not, it is still their draw and the next step adds another.
     * Reaching the knockout size, or a dry deck, ends the turn.
     */
    private fun drawUntilPlayable(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val drawn = drawOne(s, random)
        val events = mutableListOf<MonoChaosEvent>()
        val hand = s.handForSeat(seat.value) + listOfNotNull(drawn.card)
        drawn.card?.let { events += MonoChaosEvent.CardDrawn(seat, it) }
        var after = drawn.state.copy(
            hands = s.hands + (seat.value to hand),
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = null,
        )
        after = knockOut(after, seat.value, events)
        val playable = drawn.card?.isPlayableOn(after.discardPile.lastOrNull(), after.topCardColor) == true

        // Knocked out, or the deck is dry: the turn passes.
        if (after.isEliminated(seat.value) || drawn.card == null) {
            val next = nextActive(after, seat.value)
            events += MonoChaosEvent.TurnPassed(seat)
            val ready = after.copy(
                currentSeat = next,
                phase = MonoChaosPhase.AWAITING_PLAY,
                drawnCardIndex = null,
                turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
            )
            return finishOrContinue(ready, next, events)
        }
        // The card fits: play it (or pass), with the clock running. It doesn't: draw again.
        val phase = if (playable) MonoChaosPhase.AWAITING_PLAY else MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE
        return ApplyResult.Success(
            after.copy(
                phase = phase,
                drawnCardIndex = if (playable) hand.lastIndex else null,
                turn = MonoChaosTurnState(seat, phase.name),
            ),
            events,
        )
    }

    private fun stackDraw(s: MonoChaosState, seat: SeatNumber, handIndex: Int, random: GameRandom): ApplyResult {
        val hand = s.handForSeat(seat.value)
        if (handIndex !in hand.indices) return rejected("BAD_CARD", "No card at that position")
        val card = hand[handIndex]
        if (card.penaltyValue() < s.stackTopValue || card.penaltyValue() <= 0) {
            return rejected("BAD_STACK", "Cannot stack this card")
        }
        // Any draw card of equal or higher value stacks, whatever its colour (+2 < +4 = Wild Reverse +4 < +6 < +10).

        val newHand = hand.toMutableList().apply { removeAt(handIndex) }
        val total = s.activeStackPenalty + card.penaltyValue()
        // A wild draw card (+4 Reverse, +6, +10) lets its player name the colour, stacked or not:
        // the stack waits for the choice, then moves on ([chooseColor]).
        if (card.playColor() == null) {
            var choosing = s.copy(
                hands = s.hands + (seat.value to newHand),
                discardPile = s.discardPile + card,
                currentSeat = seat.value,
                phase = MonoChaosPhase.AWAITING_COLOR_CHOICE,
                activeStackPenalty = total,
                stackTopValue = card.penaltyValue(),
                turnCounter = s.turnCounter + 1,
                lastActionSeat = seat.value,
                lastActionCard = card,
                turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_COLOR_CHOICE.name),
            )
            choosing = when {
                newHand.size == 1 -> choosing.copy(monoWindowSeat = seat.value, monoCallWindowExpiry = WINDOW_OPEN, monoCalledSeat = null)
                s.monoWindowSeat == seat.value -> choosing.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
                else -> choosing
            }
            return ApplyResult.Success(choosing, listOf<MonoChaosEvent>(MonoChaosEvent.StackDraw(seat, card, total)))
        }
        val reverses = card is MonoChaosCard.PrismReverseDrawFour
        val turned = if (reverses) s.copy(reversed = !s.reversed) else s
        val victim = nextActive(turned, seat.value)
        var after = turned.copy(
            hands = s.hands + (seat.value to newHand),
            discardPile = s.discardPile + card,
            currentSeat = victim,
            phase = MonoChaosPhase.AWAITING_STACK_CHOICE,
            topCardColor = card.playColor() ?: s.topCardColor,
            activeStackPenalty = total,
            stackTopValue = card.penaltyValue(),
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = card,
            turn = MonoChaosTurnState(SeatNumber(victim), MonoChaosPhase.AWAITING_STACK_CHOICE.name),
        )
        // Stacking a card can leave one card: the MONO! window opens.
        after = when {
            newHand.size == 1 -> after.copy(monoWindowSeat = seat.value, monoCallWindowExpiry = WINDOW_OPEN, monoCalledSeat = null)
            s.monoWindowSeat == seat.value -> after.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
            else -> after
        }
        val events = listOf<MonoChaosEvent>(MonoChaosEvent.StackDraw(seat, card, total))
        return ApplyResult.Success(after, events)
    }

    private fun acceptStack(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.activeStackPenalty <= 0) return rejected("NO_STACK", "No penalty to accept")
        val events = mutableListOf<MonoChaosEvent>()
        val drawn = drawPenalty(s, seat.value, s.activeStackPenalty, random)
        var after = drawn.state.copy(
            phase = MonoChaosPhase.AWAITING_PLAY,
            activeStackPenalty = 0,
            stackTopValue = 0,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
        )
        events += MonoChaosEvent.StackAccepted(seat, drawn.taken)
        after = knockOut(after, seat.value, events)

        val next = nextActive(after, seat.value)
        events += MonoChaosEvent.TurnPassed(seat)
        val ready = after.copy(
            currentSeat = next,
            turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
        )
        return finishOrContinue(ready, next, events)
    }

    private fun chooseSwapTarget(s: MonoChaosState, seat: SeatNumber, target: Int, random: GameRandom): ApplyResult {
        if (s.phase != MonoChaosPhase.AWAITING_SWAP_CHOICE) return rejected("NOT_WAITING", "Not choosing a swap partner")
        if (target == seat.value || target !in activeSeats(s)) {
            return rejected("BAD_TARGET", "Choose an opponent")
        }
        val events = mutableListOf<MonoChaosEvent>(MonoChaosEvent.HandSwapped(seat, SeatNumber(target)))
        var after = s.copy(
            hands = s.hands + (seat.value to s.handForSeat(target)) + (target to s.handForSeat(seat.value)),
            phase = MonoChaosPhase.AWAITING_PLAY,
            pendingSwapSeat = null,
            turnCounter = s.turnCounter + 1,
        )
        // A swap can bury either player under the knockout threshold.
        after = knockOut(after, seat.value, events)
        after = knockOut(after, target, events)

        val next = nextActive(after, seat.value)
        val ready = after.copy(
            currentSeat = next,
            turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
        )
        return finishOrContinue(ready, next, events)
    }

    private fun passTurn(s: MonoChaosState, seat: SeatNumber): ApplyResult {
        val next = nextActive(s, seat.value)
        return ApplyResult.Success(
            s.copy(
                currentSeat = next,
                phase = MonoChaosPhase.AWAITING_PLAY,
                drawnCardIndex = null,
                turnCounter = s.turnCounter + 1,
                turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
            ),
            listOf(MonoChaosEvent.TurnPassed(seat)),
        )
    }

    /**
     * The 25-card knockout: the seat's cards go to the bottom of the draw
     * deck and they leave the match.
     */
    private fun knockOut(s: MonoChaosState, seat: Int, events: MutableList<MonoChaosEvent>): MonoChaosState {
        if (!s.monoChaosRules.house.knockoutEnabled) return s
        if (s.isEliminated(seat)) return s
        if (s.handForSeat(seat).size < s.monoChaosRules.mandatory.knockoutThreshold) return s
        events += MonoChaosEvent.PlayerEliminated(SeatNumber(seat))
        return s.copy(
            hands = s.hands + (seat to emptyList()),
            drawPile = s.drawPile + s.handForSeat(seat),
            eliminatedSeats = s.eliminatedSeats + seat,
        )
    }

    /** MONO!: on your own turn with two cards (before playing), or while your own catch window is open. */
    private fun callMono(s: MonoChaosState, seat: SeatNumber): ApplyResult {
        val me = seat.value
        val before = me == s.currentSeat && s.phase == MonoChaosPhase.AWAITING_PLAY && s.cardCountForSeat(me) == 2
        val after = s.catchSeat == me && s.cardCountForSeat(me) == 1
        if (!before && !after) return rejected("CANT_CALL", "No MONO! to call")
        if (s.monoCalledSeat == me) return rejected("ALREADY_CALLED", "Already called")
        return ApplyResult.Success(s.copy(monoCalledSeat = me), listOf(MonoChaosEvent.MonoCalled(seat)))
    }

    /** The players voted to stop: ranked as at any other end (fewest cards first, knocked-out seats last). */
    private fun endMatch(s: MonoChaosState): ApplyResult {
        val ended = s.copy(phase = MonoChaosPhase.FINISHED, turn = MonoChaosTurnState(SeatNumber(s.currentSeat), MonoChaosPhase.FINISHED.name))
        return ApplyResult.Success(ended, listOf(MonoChaosEvent.MatchEnded(outcome(ended).ranking.map { it.seat.value })))
    }

    private fun expireMonoCall(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.monoWindowSeat != seat.value || s.monoCallWindowExpiry == 0L) {
            return ApplyResult.Success(s, emptyList())
        }
        val closed = s.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
        val drawn = drawPenalty(closed, seat.value, s.monoChaosRules.mandatory.monoPenaltyCards, random)
        val events = mutableListOf<MonoChaosEvent>(MonoChaosEvent.MonoMissed(seat, drawn.taken))
        val after = knockOut(drawn.state, seat.value, events)
        return ApplyResult.Success(after, events)
    }

    /**
     * Hands [seat] up to [count] penalty cards, one at a time. The draw stops
     * the moment the hand reaches the knockout size: that player is out at
     * once and takes no more cards.
     */
    private fun drawPenalty(s: MonoChaosState, seat: Int, count: Int, random: GameRandom): PenaltyDraw {
        var state = s
        var taken = 0
        while (taken < count && !atKnockout(state, seat)) {
            val drawn = drawOne(state, random)
            val card = drawn.card ?: break
            state = drawn.state.copy(hands = drawn.state.hands + (seat to drawn.state.handForSeat(seat) + card))
            taken++
        }
        return PenaltyDraw(state, taken)
    }

    private fun atKnockout(s: MonoChaosState, seat: Int): Boolean =
        s.monoChaosRules.house.knockoutEnabled && s.handForSeat(seat).size >= s.monoChaosRules.mandatory.knockoutThreshold

    /** The Rule of 0: every hand moves one seat along the direction of play. */
    private fun passAllHands(s: MonoChaosState): MonoChaosState {
        val active = activeSeats(s)
        if (active.size <= 1) return s
        val moved = active.associateWith { seat -> s.handForSeat(previousActive(s, seat)) }
        return s.copy(hands = s.hands + moved)
    }

    /** Seats still in play: not knocked out, not finished. */
    private fun activeSeats(s: MonoChaosState): List<Int> =
        s.seats.map { it.value }.filter { !s.isEliminated(it) && it !in s.finishOrder }

    private fun nextActive(s: MonoChaosState, from: Int): Int = advance(s, from, 1)

    private fun previousActive(s: MonoChaosState, from: Int): Int = advance(s, from, -1)

    /** [steps] seats still in play on from [from] (which may itself be out); negative walks backwards. */
    private fun advance(s: MonoChaosState, from: Int, steps: Int): Int {
        val active = activeSeats(s)
        if (active.isEmpty()) return from
        val seats = s.seats.map { it.value }
        val n = seats.size
        var index = seats.indexOf(from).takeIf { it >= 0 } ?: return active.first()
        val direction = if (s.reversed) -steps else steps
        val step = if (direction > 0) 1 else -1
        val count = kotlin.math.abs(direction)
        var moved = 0
        var guard = 0
        while (moved < count && guard < n * (count + 1)) {
            index = ((index + step) % n + n) % n
            if (seats[index] in active) moved++
            guard++
        }
        return seats[index]
    }

    /** The seat [seat] most wants to trade hands with (largest hand). */
    private fun swapPartner(s: MonoChaosState, seat: Int): Int =
        activeSeats(s)
            .filter { it != seat }
            .maxByOrNull { s.cardCountForSeat(it) }
            ?: seat

    /** The colour the seat holds most of, so autoplay plays to its strength. */
    private fun bestColour(s: MonoChaosState, seat: Int): Color =
        Color.entries.maxByOrNull { colour ->
            s.handForSeat(seat).count { it.playColor() == colour }
        } ?: Color.RED

    /** Of the legal cards, the most punishing one. */
    private fun bestCard(s: MonoChaosState, seat: Int, legal: List<Int>): Int {
        val hand = s.handForSeat(seat)
        return legal.maxByOrNull { index ->
            val card = hand[index]
            card.penaltyValue() * 10 + if (card.isPrism()) 5 else 0
        } ?: legal.first()
    }

    private inline fun List<MonoChaosCard>.indexesOf(predicate: (MonoChaosCard) -> Boolean): List<Int> =
        mapIndexedNotNull { index, card -> if (predicate(card)) index else null }

    private fun rejected(code: String, message: String): ApplyResult =
        ApplyResult.Rejected(TypedRejection(code, message))

    private companion object {
        const val SEVEN = 7
        const val ZERO = 0
        const val CLOCKWISE = "clockwise"
        const val COUNTER = "counter-clockwise"
        /** The engine does not own a clock; the host stamps this deadline. */
        const val WINDOW_OPEN = -1L
    }
}
