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
        // MONO! is called right after playing, when the turn has already moved on.
        if (action is MonoChaosAction.CallMono) return callMono(s, seat)

        if (s.isEliminated(seat.value)) return rejected("SEAT_ELIMINATED", "Seat eliminated")
        if (seat.value != s.currentSeat) return rejected("NOT_YOUR_TURN", "Not your turn")
        // Every move only in its own phase: a modified client can't draw or pass its way out of a penalty.
        if (!allowedNow(s, action)) return rejected("BAD_PHASE", "Not allowed now")

        return when (action) {
            is MonoChaosAction.PlayCard -> playCard(s, seat, action.handIndex, random)
            MonoChaosAction.DrawUntilPlayable -> drawUntilPlayable(s, seat, random)
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
    }

    /** Which moves the phase allows. */
    private fun allowedNow(s: MonoChaosState, action: Action): Boolean {
        val playing = s.phase == MonoChaosPhase.AWAITING_PLAY || s.phase == MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE
        return when (action) {
            is MonoChaosAction.PlayCard -> playing || s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE
            MonoChaosAction.DrawUntilPlayable -> playing && s.drawnCardIndex == null
            MonoChaosAction.Pass -> playing && s.drawnCardIndex != null
            is MonoChaosAction.StackDraw, MonoChaosAction.AcceptStack -> s.phase == MonoChaosPhase.AWAITING_STACK_CHOICE
            is MonoChaosAction.ChooseColor -> s.phase == MonoChaosPhase.AWAITING_COLOR_CHOICE
            is MonoChaosAction.ChooseRouletteColor -> s.phase == MonoChaosPhase.AWAITING_ROULETTE_COLOR
            is MonoChaosAction.ChooseSwapTarget -> s.phase == MonoChaosPhase.AWAITING_SWAP_CHOICE
            else -> true
        }
    }

    override fun currentActor(state: GameState) = SeatNumber((state as MonoChaosState).currentSeat)

    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val s = state as MonoChaosState
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
                // A draw card can only be stacked on if it matches the
                // discard as well as being worth enough.
                val stackable = s.handForSeat(seat.value).withIndex()
                    .filter { (_, card) ->
                        card.penaltyValue() >= s.stackTopValue &&
                            card.penaltyValue() > 0 &&
                            card.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)
                    }
                if (stackable.isNotEmpty()) {
                    stackDraw(s, seat, stackable.maxByOrNull { it.value.penaltyValue() }!!.index, random)
                } else {
                    acceptStack(s, seat, random)
                }
            }
            MonoChaosPhase.AWAITING_SWAP_CHOICE -> chooseSwapTarget(s, seat, swapPartner(s, seat.value), random)
            MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE -> drawUntilPlayable(s, seat, random)
            MonoChaosPhase.FINISHED -> rejected("MATCH_ENDED", "Match ended")
        }
    }

    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as MonoChaosState
        if (seat.value !in s.seats.map { it.value }) return s
        val newSeats = s.seats.filter { it.value != seat.value }
        val newHands = s.hands - seat.value
        val remaining = newSeats.filter { !s.isEliminated(it.value) }
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

    override fun outcome(state: GameState): MatchOutcome {
        val s = state as MonoChaosState
        val ranking = mutableListOf<RankedSeat>()
        val standing = s.seats.filter { !s.isEliminated(it.value) }
        val winner = standing.firstOrNull { s.cardCountForSeat(it.value) == 0 }
            ?: standing.singleOrNull()
        if (winner != null) ranking.add(RankedSeat(winner, SeatStatus.FINISHED, 1))
        s.seats
            .map { it.value }
            .filter { it != winner?.value && !s.isEliminated(it) }
            .sortedBy { s.cardCountForSeat(it) }
            .forEach { ranking.add(RankedSeat(SeatNumber(it), SeatStatus.FINISHED, ranking.size + 1)) }
        s.eliminatedSeats.sorted().forEach { ranking.add(RankedSeat(SeatNumber(it), SeatStatus.REMOVED, ranking.size + 1)) }
        return MatchOutcome(ranking)
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

        // The last card wins before any swap, pass or penalty can happen.
        if (newHand.isEmpty()) {
            return ApplyResult.Success(
                played.copy(
                    phase = MonoChaosPhase.FINISHED,
                    currentSeat = seat.value,
                    turn = MonoChaosTurnState(seat, MonoChaosPhase.FINISHED.name),
                ),
                events + MonoChaosEvent.SeatFinished(seat, 1) + MonoChaosEvent.MatchEnded(listOf(seat.value)),
            )
        }

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
                val left = after.cardCountForSeat(seat.value)
                // Shedding the whole hand this way wins too.
                if (left == 0) {
                    return ApplyResult.Success(
                        after.copy(phase = MonoChaosPhase.FINISHED, currentSeat = seat.value, turn = MonoChaosTurnState(seat, MonoChaosPhase.FINISHED.name)),
                        events + MonoChaosEvent.SeatFinished(seat, 1) + MonoChaosEvent.MatchEnded(listOf(seat.value)),
                    )
                }
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
                card.number == SEVEN && s.monoChaosRules.house.sevenSwap -> ApplyResult.Success(
                    played.copy(
                        phase = MonoChaosPhase.AWAITING_SWAP_CHOICE,
                        pendingSwapSeat = seat.value,
                        currentSeat = seat.value,
                        turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_SWAP_CHOICE.name),
                    ),
                    events,
                )
                card.number == ZERO && s.monoChaosRules.house.zeroPass -> {
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

    /** Ends the match when only one seat is left standing. */
    private fun finishOrContinue(s: MonoChaosState, next: Int, events: MutableList<MonoChaosEvent>): ApplyResult {
        val standing = activeSeats(s)
        if (standing.size <= 1) {
            val winner = standing.firstOrNull() ?: next
            events += MonoChaosEvent.SeatFinished(SeatNumber(winner), 1)
            events += MonoChaosEvent.MatchEnded(listOf(winner))
            return ApplyResult.Success(
                s.copy(
                    phase = MonoChaosPhase.FINISHED,
                    currentSeat = winner,
                    turn = MonoChaosTurnState(SeatNumber(winner), MonoChaosPhase.FINISHED.name),
                ),
                events,
            )
        }
        return ApplyResult.Success(s, events)
    }

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
        // Two players: the reverse acts as a skip, so the +4 comes back to its player.
        val victim = if (reverses && activeSeats(chosen).size <= 2) seat.value else nextActive(chosen, seat.value)

        if (penalty > 0) {
            return ApplyResult.Success(openStack(chosen, victim, penalty), events)
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
     * The Roulette: flip cards until one of the chosen colour appears. All
     * of them join the victim's hand, the colour becomes live, and the
     * victim loses their turn. Multi-colour cards never match.
     */
    private fun chooseRouletteColor(s: MonoChaosState, seat: SeatNumber, color: Color, random: GameRandom): ApplyResult {
        if (s.phase != MonoChaosPhase.AWAITING_ROULETTE_COLOR) {
            return rejected("NOT_WAITING", "Not choosing a roulette colour")
        }
        val events = mutableListOf<MonoChaosEvent>()
        var drawPile = s.drawPile
        var discard = s.discardPile
        val flipped = mutableListOf<MonoChaosCard>()

        while (true) {
            if (drawPile.isEmpty() && discard.size > 1) {
                val face = discard.last()
                drawPile = discard.dropLast(1).shuffled(random)
                discard = listOf(face)
            }
            if (drawPile.isEmpty()) break
            val card = drawPile.first()
            drawPile = drawPile.drop(1)
            flipped += card
            if (card.playColor() == color) break
        }

        val newHand = s.handForSeat(seat.value) + flipped
        var after = s.copy(
            hands = s.hands + (seat.value to newHand),
            drawPile = drawPile,
            discardPile = discard,
            topCardColor = color,
            phase = MonoChaosPhase.AWAITING_PLAY,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
        )
        events += MonoChaosEvent.RouletteColorChosen(seat, color)
        if (flipped.isNotEmpty()) events += MonoChaosEvent.CardsDrawn(seat, flipped.size)

        after = knockOut(after, seat.value, events)
        val next = if (after.isEliminated(seat.value)) nextActive(after, seat.value) else nextActive(after, seat.value)
        val ready = after.copy(
            currentSeat = next,
            phase = MonoChaosPhase.AWAITING_PLAY,
            turn = MonoChaosTurnState(SeatNumber(next), MonoChaosPhase.AWAITING_PLAY.name),
        )
        return finishOrContinue(ready, next, events)
    }

    private fun drawUntilPlayable(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val events = mutableListOf<MonoChaosEvent>()
        var drawPile = s.drawPile
        var discard = s.discardPile
        val hand = s.handForSeat(seat.value).toMutableList()
        var drawn = 0
        var playableIndex: Int? = null

        while (true) {
            if (drawPile.isEmpty() && discard.size > 1) {
                val face = discard.last()
                drawPile = discard.dropLast(1).shuffled(random)
                discard = listOf(face)
            }
            if (drawPile.isEmpty()) break
            val card = drawPile.first()
            drawPile = drawPile.drop(1)
            hand += card
            drawn++
            if (s.monoChaosRules.house.knockoutEnabled && hand.size >= s.monoChaosRules.mandatory.knockoutThreshold) break
            if (card.isPlayableOn(discard.lastOrNull(), s.topCardColor)) {
                playableIndex = hand.lastIndex
                break
            }
        }

        if (drawn > 0) events += MonoChaosEvent.CardsDrawn(seat, drawn)

        var after = s.copy(
            hands = s.hands + (seat.value to hand),
            drawPile = drawPile,
            discardPile = discard,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = null,
        )
        after = knockOut(after, seat.value, events)

        // Knocked out, or the deck is dry: the turn passes.
        if (after.isEliminated(seat.value) || playableIndex == null) {
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

        // A playable card is in hand: play it (or pass), with the clock running.
        return ApplyResult.Success(
            after.copy(
                phase = MonoChaosPhase.AWAITING_PLAY,
                drawnCardIndex = playableIndex,
                turn = MonoChaosTurnState(seat, MonoChaosPhase.AWAITING_PLAY.name),
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
        val reverses = card is MonoChaosCard.PrismReverseDrawFour
        val turned = if (reverses) s.copy(reversed = !s.reversed) else s
        val victim = if (reverses && activeSeats(turned).size <= 2) seat.value else nextActive(turned, seat.value)
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
        // Stacking the last card wins.
        if (newHand.isEmpty()) {
            return ApplyResult.Success(
                after.copy(phase = MonoChaosPhase.FINISHED, currentSeat = seat.value, turn = MonoChaosTurnState(seat, MonoChaosPhase.FINISHED.name)),
                events + MonoChaosEvent.SeatFinished(seat, 1) + MonoChaosEvent.MatchEnded(listOf(seat.value)),
            )
        }
        return ApplyResult.Success(after, events)
    }

    private fun acceptStack(s: MonoChaosState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.activeStackPenalty <= 0) return rejected("NO_STACK", "No penalty to accept")
        val penalty = s.activeStackPenalty
        val events = mutableListOf<MonoChaosEvent>()

        var drawPile = s.drawPile
        var discard = s.discardPile
        val taken = mutableListOf<MonoChaosCard>()
        repeat(penalty) {
            if (drawPile.isEmpty() && discard.size > 1) {
                val face = discard.last()
                drawPile = discard.dropLast(1).shuffled(random)
                discard = listOf(face)
            }
            if (drawPile.isNotEmpty()) {
                taken += drawPile.first()
                drawPile = drawPile.drop(1)
            }
        }

        var after = s.copy(
            hands = s.hands + (seat.value to s.handForSeat(seat.value) + taken),
            drawPile = drawPile,
            discardPile = discard,
            phase = MonoChaosPhase.AWAITING_PLAY,
            activeStackPenalty = 0,
            stackTopValue = 0,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
        )
        events += MonoChaosEvent.StackAccepted(seat, penalty)
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
        if (target == seat.value || target !in s.seats.map { it.value } || s.isEliminated(target)) {
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

    private fun callMono(s: MonoChaosState, seat: SeatNumber): ApplyResult {
        if (s.monoWindowSeat != seat.value || s.monoCallWindowExpiry == 0L) {
            return rejected("WINDOW_CLOSED", "No MONO! window open for you")
        }
        if (s.cardCountForSeat(seat.value) != 1) return rejected("NOT_ONE_CARD", "Call MONO! with one card")
        return ApplyResult.Success(
            s.copy(monoCalledSeat = seat.value, monoWindowSeat = null, monoCallWindowExpiry = 0),
            listOf(MonoChaosEvent.MonoCalled(seat)),
        )
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
        val penalty = s.monoChaosRules.mandatory.monoPenaltyCards
        var drawPile = closed.drawPile
        var discard = closed.discardPile
        val cards = mutableListOf<MonoChaosCard>()
        repeat(penalty) {
            if (drawPile.isEmpty() && discard.size > 1) {
                val face = discard.last()
                drawPile = discard.dropLast(1).shuffled(random)
                discard = listOf(face)
            }
            if (drawPile.isNotEmpty()) {
                cards += drawPile.first()
                drawPile = drawPile.drop(1)
            }
        }
        val events = mutableListOf<MonoChaosEvent>(MonoChaosEvent.MonoMissed(seat, cards.size))
        var after = closed.copy(
            hands = closed.hands + (seat.value to closed.handForSeat(seat.value) + cards),
            drawPile = drawPile,
            discardPile = discard,
        )
        after = knockOut(after, seat.value, events)
        return ApplyResult.Success(after, events)
    }

    /** The Rule of 0: every hand moves one seat along the direction of play. */
    private fun passAllHands(s: MonoChaosState): MonoChaosState {
        val active = activeSeats(s)
        if (active.size <= 1) return s
        val moved = active.associateWith { seat -> s.handForSeat(previousActive(s, seat)) }
        return s.copy(hands = s.hands + moved)
    }

    private fun activeSeats(s: MonoChaosState): List<Int> =
        s.seats.map { it.value }.filter { !s.isEliminated(it) }

    private fun nextActive(s: MonoChaosState, from: Int): Int = advance(s, from, 1)

    private fun previousActive(s: MonoChaosState, from: Int): Int = advance(s, from, -1)

    /** [steps] active seats on from [from]; negative walks backwards. */
    private fun advance(s: MonoChaosState, from: Int, steps: Int): Int {
        val active = activeSeats(s)
        if (active.isEmpty()) return from
        val start = active.indexOf(from).takeIf { it >= 0 } ?: 0
        val size = active.size
        var index = start
        val direction = if (s.reversed) -steps else steps
        repeat(kotlin.math.abs(direction)) {
            index = ((index + if (direction > 0) 1 else -1) % size + size) % size
        }
        return active[index]
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
