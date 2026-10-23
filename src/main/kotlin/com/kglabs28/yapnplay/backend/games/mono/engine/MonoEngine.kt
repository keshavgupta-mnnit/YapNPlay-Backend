package com.kglabs28.yapnplay.backend.games.mono.engine

import com.kglabs28.yapnplay.backend.games.base.Game
import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoEvent
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoTurnState
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoView
import com.kglabs28.yapnplay.backend.games.mono.domain.buildMonoDeck
import com.kglabs28.yapnplay.backend.games.mono.domain.isPlayableOn
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoRules
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants

/**
 * Standard MONO: match on colour, number or symbol; Reverse flips the
 * direction of play, Skip misses a player, +2 and a Prism +4 charge the
 * next player, and a draw is strictly one card. Pure: [random] drives
 * every shuffle, and the host stamps the MONO! deadline.
 */
class MonoEngine : Game {
    override val id = GameId(MonoConstants.ID)
    override val displayName = MonoConstants.DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = MonoConstants.TURN_SECONDS)
    override val matchmaking = MatchmakingConfig(
        seats = MonoConstants.QUICK_PLAY_SEATS,
        searchSeconds = MonoConstants.SEARCH_SECONDS,
    )
    override val showcase = GameShowcase(taglines = MonoConstants.TAGLINES, artKey = MonoConstants.ID)
    override val houseRuleOptions = MonoConstants.HOUSE_RULES
    override val fixedRules = MonoConstants.FIXED_RULES
    override val minPlayers = MonoConstants.PLAYERS_MIN
    override val maxPlayers = MonoConstants.PLAYERS_MAX
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Sequential
    override val bot: GameBot = MonoBot()

    override fun defaultRules(): GameRules = MonoRules.TRADITIONAL

    override fun rulesSummary(): String = MonoConstants.FIXED_RULES.joinToString(". ")

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        val monoRules = rules as? MonoRules ?: MonoRules.TRADITIONAL
        val deck = buildMonoDeck().shuffled(random)
        val seats = (1..seatCount).map { SeatNumber(it) }
        val handSize = monoRules.mandatory.startingHandSize

        val hands = mutableMapOf<Int, List<MonoCard>>()
        var cardIndex = 0
        for (seat in 1..seatCount) {
            hands[seat] = deck.subList(cardIndex, cardIndex + handSize)
            cardIndex += handSize
        }
        val rest = deck.subList(cardIndex, deck.size).toMutableList()

        // The starting card is always a number card (no action on the first player): keep flipping.
        while (rest[0] !is MonoCard.NumberCard) rest.add(rest.removeAt(0))
        val face = rest.removeAt(0)

        val firstSeat = random.nextInt(seatCount) + 1
        return MonoState(
            _gameId = id, _rules = monoRules, _seats = seats,
            _turn = MonoTurnState(SeatNumber(firstSeat), MonoPhase.AWAITING_PLAY.name),
            hands = hands, drawPile = rest, discardPile = listOf(face),
            currentSeat = firstSeat,
            phase = MonoPhase.AWAITING_PLAY,
            topCardColor = face.playColor(),
        )
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as MonoState
        if (s.phase == MonoPhase.FINISHED) return rejected("MATCH_ENDED", "Match ended")

        // The MONO! window can shut from outside a seat's turn.
        if (action is MonoAction.ExpireMonoCall) return expireMonoCall(s, seat, random)
        // The server ends the match early (the players voted to stop).
        if (action is MonoAction.EndMatch) return endMatch(s)
        // MONO! and Caught can come from any seat at any moment.
        if (action is MonoAction.CallMono) return callMono(s, seat)
        if (action is MonoAction.Caught) return caught(s, seat, random)

        if (seat.value != s.currentSeat) return rejected("NOT_YOUR_TURN", "Not your turn")
        // Every move only in its own phase: a modified client can't draw or pass its way out of a penalty.
        if (!allowedNow(s, action)) return rejected("BAD_PHASE", "Not allowed now")

        val result = when (action) {
            is MonoAction.PlayCard -> playCard(s, seat, action.handIndex, random)
            MonoAction.DrawCard -> drawCard(s, seat, random)
            MonoAction.Pass -> passTurn(s, seat)
            is MonoAction.ChooseColor -> chooseColor(s, seat, action.color, random)
            is MonoAction.ChooseSwapTarget -> chooseSwapTarget(s, seat, action.seat)
            MonoAction.CallMono -> callMono(s, seat)
            MonoAction.AcceptStack -> acceptStack(s, seat, random)
            MonoAction.ExpireMonoCall, MonoAction.EndMatch -> rejected("BAD_REQUEST", "Handled above")
            else -> rejected("BAD_REQUEST", "Unknown action for MONO")
        }
        return if (result is ApplyResult.Success) settle(catchWindow(s, result.state as MonoState, seat.value), result.events) else result
    }

    /**
     * Play goes on after a seat empties its hand: it joins
     * [MonoState.finishOrder] and is skipped from then on (it only names the
     * colour of a last wild). The match ends when one seat or none is still
     * holding cards.
     */
    private fun settle(after: MonoState, events: List<GameEvent>): ApplyResult {
        var s = after
        val out = events.toMutableList()
        s.seats.map { it.value }.filter { it !in s.finishOrder && s.handForSeat(it).isEmpty() }.forEach { seat ->
            s = s.copy(finishOrder = s.finishOrder + seat)
            out += MonoEvent.SeatFinished(SeatNumber(seat), s.finishOrder.size)
        }
        if (activeSeats(s).size <= 1) {
            val ended = s.copy(
                phase = MonoPhase.FINISHED, catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet(), monoWindowSeat = null,
                turn = MonoTurnState(SeatNumber(s.currentSeat), MonoPhase.FINISHED.name),
            )
            return ApplyResult.Success(ended, out + MonoEvent.MatchEnded(outcome(ended).ranking.map { it.seat.value }))
        }
        if (s.currentSeat in s.finishOrder && s.phase != MonoPhase.AWAITING_COLOR_CHOICE) {
            val next = advance(s, s.currentSeat, 1, s.reversed)
            val phase = if (s.phase == MonoPhase.AWAITING_STACK_CHOICE) s.phase else MonoPhase.AWAITING_PLAY
            s = s.copy(currentSeat = next, phase = phase, drawnCardIndex = null, pendingSwapSeat = null, turn = MonoTurnState(SeatNumber(next), phase.name))
        }
        return ApplyResult.Success(s, out)
    }

    /**
     * The catch window, after every move: it opens when [actor] goes down to
     * one card (called or not) and stays open for as long as they hold that
     * one card, whoever's turn it is: anyone may catch an uncalled MONO at
     * any time. A pending call that didn't end on one card is dropped when
     * the turn moves on.
     */
    private fun catchWindow(before: MonoState, after: MonoState, actor: Int): MonoState {
        var s = after
        s.catchSeat?.let { target ->
            if (s.handForSeat(target).size != 1) s = s.copy(catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet())
        }
        if (before.handForSeat(actor).size > 1 && s.handForSeat(actor).size == 1) {
            s = s.copy(catchSeat = actor, catchNextSeat = null, catchClosedFor = emptySet(), monoCalledSeat = if (before.monoCalledSeat == actor) actor else null)
        }
        if (s.catchSeat != null && s.catchNextSeat == null && s.currentSeat != s.catchSeat) s = s.copy(catchNextSeat = s.currentSeat)
        // A call only counts for the turn it was made in.
        if (s.monoCalledSeat != null && s.monoCalledSeat != s.catchSeat && s.currentSeat != s.monoCalledSeat) s = s.copy(monoCalledSeat = null)
        // The old timed window is replaced by the catch window.
        return s.copy(monoWindowSeat = s.catchSeat, monoCallWindowExpiry = 0)
    }

    /** Caught: the player on one card hadn't called (they draw), or had (the presser draws, once per window). */
    private fun caught(s: MonoState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val target = s.catchSeat
        if (seat.value == target || seat.value !in s.seats.map { it.value } || seat.value in s.finishOrder) return rejected("BAD_TARGET", "You can't catch yourself")
        if (target != null && seat.value in s.catchClosedFor) return rejected("ALREADY_TRIED", "One try per player")
        // Pressing CAUGHT with nobody to catch, or on a player who called MONO, is a wrong catch: the presser pays.
        return if (target == null || s.monoCalledSeat == target) {
            val penalty = s.monoRules.mandatory.falseCatchPenaltyCards
            val drawn = drawCards(s, penalty, random)
            val closed = if (target == null) s.catchClosedFor else s.catchClosedFor + seat.value
            ApplyResult.Success(
                drawn.state.copy(hands = s.hands + (seat.value to s.handForSeat(seat.value) + drawn.cards), catchClosedFor = closed),
                drawn.cards.map { MonoEvent.CardDrawn(seat, it) } + MonoEvent.FalseCatch(seat, SeatNumber(target ?: seat.value), drawn.cards.size),
            )
        } else {
            val penalty = s.monoRules.mandatory.monoPenaltyCards
            val drawn = drawCards(s, penalty, random)
            ApplyResult.Success(
                drawn.state.copy(
                    hands = s.hands + (target to s.handForSeat(target) + drawn.cards),
                    catchSeat = null, catchNextSeat = null, catchClosedFor = emptySet(), monoWindowSeat = null, monoCallWindowExpiry = 0,
                ),
                drawn.cards.map { MonoEvent.CardDrawn(SeatNumber(target), it) } + MonoEvent.MonoCaught(seat, SeatNumber(target), drawn.cards.size),
            )
        }
    }

    /**
     * The hand positions [seat] may play now: matching cards (only the drawn
     * one after a draw, unless No Power Finish is on), never a power card as
     * the last card under No Power Finish.
     */
    fun legalPlays(s: MonoState, seat: Int): List<Int> {
        val hand = s.handForSeat(seat)
        val noPowerFinish = s.monoRules.house.noPowerFinish
        val candidates = s.drawnCardIndex?.takeIf { !noPowerFinish }?.let { listOf(it) } ?: hand.indices.toList()
        return candidates.filter { i ->
            hand[i].isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor) && !(noPowerFinish && hand.size == 1 && hand[i] !is MonoCard.NumberCard)
        }
    }

    /** Which moves the phase allows. */
    private fun allowedNow(s: MonoState, action: Action): Boolean = when (action) {
        is MonoAction.PlayCard -> s.phase == MonoPhase.AWAITING_PLAY || s.phase == MonoPhase.AWAITING_STACK_CHOICE
        MonoAction.DrawCard -> s.phase == MonoPhase.AWAITING_PLAY && s.drawnCardIndex == null
        MonoAction.Pass -> s.phase == MonoPhase.AWAITING_PLAY && s.drawnCardIndex != null
        MonoAction.AcceptStack -> s.phase == MonoPhase.AWAITING_STACK_CHOICE
        is MonoAction.ChooseColor -> s.phase == MonoPhase.AWAITING_COLOR_CHOICE
        is MonoAction.ChooseSwapTarget -> s.phase == MonoPhase.AWAITING_SWAP_CHOICE
        else -> true
    }

    override fun currentActor(state: GameState): SeatNumber = SeatNumber((state as MonoState).currentSeat)

    /**
     * Plays [seat]'s turn for them (a timeout, a bot's fallback). It ends the
     * way every move does — a seat that empties its hand finishes, and the
     * catch window moves on — exactly as through [apply].
     */
    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val s = state as MonoState
        val result = autoMove(s, seat, random)
        return if (result is ApplyResult.Success) settle(catchWindow(s, result.state as MonoState, seat.value), result.events) else result
    }

    /** The move [autoplay] makes, before the end-of-move bookkeeping. */
    private fun autoMove(s: MonoState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.phase == MonoPhase.FINISHED) return rejected("MATCH_ENDED", "Match ended")
        if (seat.value != s.currentSeat) return rejected("NOT_YOUR_TURN", "Not your turn")

        return when (s.phase) {
            MonoPhase.AWAITING_COLOR_CHOICE ->
                chooseColor(s, seat, bestColour(s, seat.value), random)
            MonoPhase.AWAITING_SWAP_CHOICE ->
                chooseSwapTarget(s, seat, swapPartner(s, seat.value))
            MonoPhase.AWAITING_STACK_CHOICE ->
                if (s.monoRules.house.stackingAllowed) {
                    // A draw card can only be stacked on if it matches the
                    // discard as well as being worth enough.
                    val stackable = s.handForSeat(seat.value).withIndex()
                        .filter { (_, card) ->
                            card.penaltyValue() >= s.stackTopValue &&
                                card.penaltyValue() > 0 &&
                                card.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)
                        }
                    if (stackable.isNotEmpty()) {
                        playCard(s, seat, stackable.maxByOrNull { it.value.penaltyValue() }!!.index, random)
                    } else {
                        acceptStack(s, seat, random)
                    }
                } else {
                    acceptStack(s, seat, random)
                }
            MonoPhase.AWAITING_PLAY -> {
                val allowed = legalPlays(s, seat.value)
                when {
                    allowed.isNotEmpty() -> playCard(s, seat, allowed.first(), random)
                    s.drawnCardIndex != null -> passTurn(s, seat)
                    else -> drawCard(s, seat, random)
                }
            }
            MonoPhase.FINISHED -> rejected("MATCH_ENDED", "Match ended")
        }
    }

    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as MonoState
        if (seat.value !in s.seats.map { it.value }) return s
        val newSeats = s.seats.filter { it.value != seat.value }
        val newHands = s.hands - seat.value
        // One player or none left holding cards: the match is over.
        if (newSeats.count { it.value !in s.finishOrder } <= 1) {
            val winner = newSeats.firstOrNull()?.value ?: 0
            return s.copy(
                seats = newSeats,
                hands = newHands,
                phase = MonoPhase.FINISHED,
                turn = MonoTurnState(SeatNumber(winner.coerceAtLeast(1)), MonoPhase.FINISHED.name),
            )
        }
        val nextSeat = if (s.currentSeat == seat.value) nextSeat(s, seat.value) else s.currentSeat
        return s.copy(
            seats = newSeats,
            hands = newHands,
            currentSeat = nextSeat,
            turn = MonoTurnState(SeatNumber(nextSeat), s.phase.name),
        )
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView {
        val s = state as MonoState
        val ownHand = (viewer as? Viewer.Player)?.let { s.handForSeat(it.seat.value) } ?: emptyList()
        return MonoView(
            seats = s.seats.map { it.value },
            currentSeat = s.currentSeat,
            phase = s.phase.name,
            topCard = s.discardPile.lastOrNull(),
            topCardColor = s.topCardColor,
            hand = ownHand,
            opponentCardCounts = s.seats.associate { it.value to s.cardCountForSeat(it.value) },
            drawPileCount = s.drawPile.size,
            discardPileCount = s.discardPile.size,
            activeStackPenalty = s.activeStackPenalty,
            monoCallWindowExpiry = s.monoCallWindowExpiry,
            monoWindowSeat = s.monoWindowSeat,
            monoCalledSeat = s.monoCalledSeat,
            reversed = s.reversed,
            drawnCardIndex = s.drawnCardIndex,
            pendingSwapSeat = s.pendingSwapSeat,
        )
    }

    override fun isFinished(state: GameState): Boolean = (state as MonoState).phase == MonoPhase.FINISHED

    /**
     * Finished seats in the order they finished, then everyone still holding
     * cards by fewest cards (a tie: whoever plays sooner from the current
     * turn), then seats that left.
     */
    override fun outcome(state: GameState): MatchOutcome {
        val s = state as MonoState
        val holding = s.seats.map { it.value }
            .filter { it !in s.finishOrder && it !in s.removedSeats }
            .sortedWith(compareBy({ s.cardCountForSeat(it) }, { turnDistance(s, it) }))
        val ranking = (s.finishOrder + holding).mapIndexed { i, seat -> RankedSeat(SeatNumber(seat), SeatStatus.FINISHED, i + 1) }.toMutableList()
        s.removedSeats.filter { it !in s.finishOrder }.forEach { ranking += RankedSeat(SeatNumber(it), SeatStatus.REMOVED, ranking.size + 1) }
        return MatchOutcome(ranking)
    }

    /** Turns until [seat] plays, counted from the current seat in the direction of play. */
    private fun turnDistance(s: MonoState, seat: Int): Int {
        val seats = s.seats.map { it.value }
        val n = seats.size
        val from = seats.indexOf(s.currentSeat).coerceAtLeast(0)
        val at = seats.indexOf(seat)
        return if (s.reversed) (from - at + n) % n else (at - from + n) % n
    }

    private fun playCard(s: MonoState, seat: SeatNumber, handIndex: Int, random: GameRandom): ApplyResult {
        val hand = s.handForSeat(seat.value)
        if (handIndex !in hand.indices) return rejected("BAD_CARD", "No card at that position")
        // A penalty on the table can only be stacked onto or taken.
        if (s.phase == MonoPhase.AWAITING_STACK_CHOICE) return stackCard(s, seat, handIndex, random)
        // After drawing, the drawn card is the only one in play.
        s.drawnCardIndex?.let { drawn ->
            if (handIndex != drawn && !s.monoRules.house.noPowerFinish) return rejected("MUST_PLAY_DRAWN", "After a draw you play the drawn card")
        }
        val card = hand[handIndex]
        if (s.monoRules.house.noPowerFinish && hand.size == 1 && card !is MonoCard.NumberCard) {
            return rejected("NO_POWER_FINISH", "Your last card must be a number card: draw instead")
        }
        if (!card.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)) {
            return rejected("NOT_PLAYABLE", "Card does not match")
        }

        val events = mutableListOf<MonoEvent>()
        val newHand = hand.toMutableList().apply { removeAt(handIndex) }
        val newHands = s.hands + (seat.value to newHand)
        val newDiscard = s.discardPile + card

        // A Prism card leaves the colour open until its player chooses.
        val prism = card.isPrism()
        val stacking = s.monoRules.house.stackingAllowed && card.penaltyValue() > 0
        val penalty = s.activeStackPenalty + card.penaltyValue()

        var played = s.copy(
            hands = newHands,
            discardPile = newDiscard,
            phase = when {
                prism -> MonoPhase.AWAITING_COLOR_CHOICE
                stacking -> MonoPhase.AWAITING_STACK_CHOICE
                else -> MonoPhase.AWAITING_PLAY
            },
            topCardColor = if (prism) s.topCardColor else card.playColor(),
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = card,
            activeStackPenalty = if (stacking) penalty else 0,
            stackTopValue = if (stacking) card.penaltyValue() else 0,
            drawnCardIndex = null,
        )

        // Going to one card opens the callout window for this seat.
        if (newHand.size == 1) {
            played = played.copy(monoWindowSeat = seat.value, monoCallWindowExpiry = WINDOW_OPEN, monoCalledSeat = null)
        } else if (s.monoWindowSeat == seat.value) {
            played = played.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
        }

        events += MonoEvent.CardPlayed(seat, card, played.topCardColor)

        // A last card still takes effect (the seat finishes in [settle]); only a swap or pass is skipped.
        val lastCard = newHand.isEmpty()

        if (prism) return ApplyResult.Success(played, events)

        if (stacking) {
            val next = nextSeat(s, seat.value)
            return ApplyResult.Success(
                played.copy(currentSeat = next, turn = MonoTurnState(SeatNumber(next), played.phase.name)),
                events,
            )
        }

        if (card.penaltyValue() > 0) {
            // +2 and +4 charge the next player, who then loses the turn.
            val victim = nextSeat(s, seat.value)
            return resolveDraw(played, victim, card.penaltyValue(), random, events)
        }

        // The Rule of 7 waits for its player to pick a partner.
        if (card is MonoCard.NumberCard && card.number == SEVEN && s.monoRules.house.sevenSwap && !lastCard) {
            return ApplyResult.Success(
                played.copy(
                    phase = MonoPhase.AWAITING_SWAP_CHOICE,
                    pendingSwapSeat = seat.value,
                    turn = MonoTurnState(seat, MonoPhase.AWAITING_SWAP_CHOICE.name),
                ),
                events,
            )
        }

        val afterRules = if (card is MonoCard.NumberCard && card.number == ZERO && s.monoRules.house.zeroPass && !lastCard) {
            events += MonoEvent.HandsPassed(if (s.reversed) COUNTER else CLOCKWISE)
            passAllHands(played, s.reversed)
        } else {
            played
        }

        val reverseFlip = card is MonoCard.ReverseCard
        val newReversed = if (reverseFlip) !s.reversed else s.reversed
        val next = nextFor(s, seat.value, card, newReversed)
        return ApplyResult.Success(
            afterRules.copy(
                reversed = newReversed,
                currentSeat = next,
                phase = MonoPhase.AWAITING_PLAY,
                turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name),
            ),
            events,
        )
    }

    /**
     * Stacks a draw card onto the penalty on the table. The card has to be
     * worth at least as much as the one that set the penalty, and it has
     * to match the discard.
     */
    private fun stackCard(s: MonoState, seat: SeatNumber, handIndex: Int, random: GameRandom): ApplyResult {
        val hand = s.handForSeat(seat.value)
        val card = hand[handIndex]
        if (card.penaltyValue() <= 0 || card.penaltyValue() < s.stackTopValue) {
            return rejected("BAD_STACK", "Cannot stack this card")
        }
        if (!card.isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)) {
            return rejected("NOT_PLAYABLE", "Card does not match")
        }
        val newHand = hand.toMutableList().apply { removeAt(handIndex) }
        if (newHand.isEmpty() && s.monoRules.house.noPowerFinish) return rejected("NO_POWER_FINISH", "Your last card must be a number card")
        val total = s.activeStackPenalty + card.penaltyValue()
        val next = nextSeat(s, seat.value)
        val after = s.copy(
            hands = s.hands + (seat.value to newHand),
            discardPile = s.discardPile + card,
            currentSeat = next,
            phase = MonoPhase.AWAITING_STACK_CHOICE,
            topCardColor = card.playColor() ?: s.topCardColor,
            activeStackPenalty = total,
            stackTopValue = card.penaltyValue(),
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = card,
            turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_STACK_CHOICE.name),
        )
        return ApplyResult.Success(after, listOf(MonoEvent.CardPlayed(seat, card, after.topCardColor)))
    }

    /** Which seat plays after [from] played [card], given [reversed]. */
    private fun nextFor(s: MonoState, from: Int, card: MonoCard, reversed: Boolean): Int = when {
        // Reverse: in a two-player match it acts as a Skip, so it comes back.
        card is MonoCard.ReverseCard -> {
            if (activeSeats(s).size <= 2) from else advance(s, from, 1, reversed)
        }
        card is MonoCard.SkipCard -> advance(s, from, 2, reversed)
        else -> advance(s, from, 1, reversed)
    }

    private fun chooseColor(s: MonoState, seat: SeatNumber, color: MonoConstants.Color, random: GameRandom): ApplyResult {
        if (s.phase != MonoPhase.AWAITING_COLOR_CHOICE) return rejected("NOT_WAITING", "Not choosing a colour")
        val top = s.discardPile.lastOrNull()
        val chosen = s.copy(
            topCardColor = color,
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            phase = MonoPhase.AWAITING_PLAY,
        )
        val events = mutableListOf<MonoEvent>(MonoEvent.ColorChosen(seat, color))

        // Stack Upward: after the colour, the next player may stack onto the +4 or take it.
        if (top is MonoCard.PrismDrawFour && s.monoRules.house.stackingAllowed) {
            val victim = nextSeat(s, seat.value)
            return ApplyResult.Success(
                chosen.copy(
                    phase = MonoPhase.AWAITING_STACK_CHOICE, currentSeat = victim,
                    activeStackPenalty = s.activeStackPenalty.coerceAtLeast(top.penaltyValue()), stackTopValue = top.penaltyValue(),
                    turn = MonoTurnState(SeatNumber(victim), MonoPhase.AWAITING_STACK_CHOICE.name),
                ),
                events,
            )
        }
        // A Prism +4 charges the next player after the colour is named.
        if (top is MonoCard.PrismDrawFour) {
            val victim = nextSeat(s, seat.value)
            return resolveDraw(chosen, victim, top.penaltyValue(), random, events)
        }

        val next = nextSeat(s, seat.value)
        return ApplyResult.Success(
            chosen.copy(currentSeat = next, turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name)),
            events,
        )
    }

    private fun chooseSwapTarget(s: MonoState, seat: SeatNumber, target: Int): ApplyResult {
        if (s.phase != MonoPhase.AWAITING_SWAP_CHOICE) return rejected("NOT_WAITING", "Not choosing a swap partner")
        if (target == seat.value) return rejected("BAD_TARGET", "Choose an opponent")
        if (target !in activeSeats(s)) return rejected("BAD_TARGET", "No such seat")
        val mine = s.handForSeat(seat.value)
        val theirs = s.handForSeat(target)
        val swapped = s.copy(
            hands = s.hands + (seat.value to theirs) + (target to mine),
            phase = MonoPhase.AWAITING_PLAY,
            pendingSwapSeat = null,
            turnCounter = s.turnCounter + 1,
        )
        val next = nextSeat(s, seat.value)
        return ApplyResult.Success(
            swapped.copy(currentSeat = next, turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name)),
            listOf(MonoEvent.HandSwapped(seat, SeatNumber(target))),
        )
    }

    private fun drawCard(s: MonoState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.drawnCardIndex != null) return rejected("ALREADY_DREW", "Play the drawn card or pass")
        val drawn = drawCards(s, 1, random)
        val card = drawn.cards.firstOrNull()
            ?: return ApplyResult.Success(passTurnState(s, seat), listOf(MonoEvent.TurnPassed(seat)))

        val newHand = s.handForSeat(seat.value) + card
        val drawnState = drawn.state.copy(
            hands = s.hands + (seat.value to newHand),
            turnCounter = s.turnCounter + 1,
            lastActionSeat = seat.value,
            lastActionCard = card,
        )
        val events = mutableListOf<MonoEvent>(MonoEvent.CardDrawn(seat, card))

        // Playable: the player has the rest of their turn to use it (under No Power Finish, any legal card).
        val canPlay = if (s.monoRules.house.noPowerFinish) {
            legalPlays(drawnState.copy(drawnCardIndex = null), seat.value).isNotEmpty()
        } else {
            card.isPlayableOn(drawnState.discardPile.lastOrNull(), drawnState.topCardColor)
        }
        if (canPlay) {
            return ApplyResult.Success(
                drawnState.copy(drawnCardIndex = newHand.lastIndex, phase = MonoPhase.AWAITING_PLAY),
                events,
            )
        }
        // Not playable: the turn passes, exactly as the rules say.
        val next = nextSeat(s, seat.value)
        events += MonoEvent.TurnPassed(seat)
        return ApplyResult.Success(
            drawnState.copy(
                currentSeat = next,
                phase = MonoPhase.AWAITING_PLAY,
                drawnCardIndex = null,
                turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name),
            ),
            events,
        )
    }

    private fun passTurn(s: MonoState, seat: SeatNumber): ApplyResult = ApplyResult.Success(
        passTurnState(s, seat),
        listOf(MonoEvent.TurnPassed(seat)),
    )

    private fun passTurnState(s: MonoState, seat: SeatNumber): MonoState {
        val next = nextSeat(s, seat.value)
        return s.copy(
            currentSeat = next,
            phase = MonoPhase.AWAITING_PLAY,
            drawnCardIndex = null,
            turnCounter = s.turnCounter + 1,
            turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name),
        )
    }

    /** [victim] draws [count] cards and loses their turn. */
    private fun resolveDraw(
        s: MonoState,
        victim: Int,
        count: Int,
        random: GameRandom,
        events: MutableList<MonoEvent>,
    ): ApplyResult {
        val drawn = drawCards(s, count, random)
        val newHand = s.handForSeat(victim) + drawn.cards
        val next = advance(drawn.state, victim, 1, s.reversed)
        val drawnEvents: List<MonoEvent> = drawn.cards.map { MonoEvent.CardDrawn(SeatNumber(victim), it) }
        events += drawnEvents
        events += MonoEvent.TurnPassed(SeatNumber(victim))
        return ApplyResult.Success(
            drawn.state.copy(
                hands = s.hands + (victim to newHand),
                currentSeat = next,
                phase = MonoPhase.AWAITING_PLAY,
                activeStackPenalty = 0,
                stackTopValue = 0,
                drawnCardIndex = null,
                turnCounter = s.turnCounter + 1,
                turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name),
            ),
            events,
        )
    }

    /** The cards drawn, and the piles they came from. */
    private data class DrawResult(val state: MonoState, val cards: List<MonoCard>)

    /**
     * Draws up to [count] cards, recycling the discard (face card aside)
     * when the draw pile runs dry.
     */
    private fun drawCards(s: MonoState, count: Int, random: GameRandom): DrawResult {
        var drawPile = s.drawPile
        var discard = s.discardPile
        val cards = mutableListOf<MonoCard>()
        repeat(count) {
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
        return DrawResult(s.copy(drawPile = drawPile, discardPile = discard), cards)
    }

    private fun acceptStack(s: MonoState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val penalty = s.activeStackPenalty
        if (penalty <= 0) return rejected("NO_STACK", "No penalty to accept")
        val drawn = drawCards(s, penalty, random)
        val newHand = s.handForSeat(seat.value) + drawn.cards
        val next = nextSeat(s, seat.value)
        val events = drawn.cards.map { MonoEvent.CardDrawn(seat, it) } + MonoEvent.TurnPassed(seat)
        return ApplyResult.Success(
            drawn.state.copy(
                hands = s.hands + (seat.value to newHand),
                currentSeat = next,
                phase = MonoPhase.AWAITING_PLAY,
                activeStackPenalty = 0,
                stackTopValue = 0,
                drawPile = drawn.state.drawPile,
                discardPile = drawn.state.discardPile,
                turnCounter = s.turnCounter + 1,
                turn = MonoTurnState(SeatNumber(next), MonoPhase.AWAITING_PLAY.name),
            ),
            events,
        )
    }

    /**
     * MONO!: on your own turn holding two cards, before playing the second-last;
     * or (unless Call Before Play is on) while your own catch window is open.
     */
    private fun callMono(s: MonoState, seat: SeatNumber): ApplyResult {
        val me = seat.value
        val before = me == s.currentSeat && s.phase == MonoPhase.AWAITING_PLAY && s.handForSeat(me).size == 2
        val after = !s.monoRules.house.callBeforePlay && s.catchSeat == me && s.handForSeat(me).size == 1
        if (!before && !after) return rejected("CANT_CALL", if (s.monoRules.house.callBeforePlay) "Call MONO! on your turn with two cards" else "No MONO! to call")
        if (s.monoCalledSeat == me) return rejected("ALREADY_CALLED", "Already called")
        return ApplyResult.Success(s.copy(monoCalledSeat = me), listOf(MonoEvent.MonoCalled(seat)))
    }

    /** The window ran out with no call: the seat takes the penalty. */
    /** The players voted to stop: ranked by fewest cards, as at any other end. */
    private fun endMatch(s: MonoState): ApplyResult {
        val ended = s.copy(phase = MonoPhase.FINISHED, turn = MonoTurnState(SeatNumber(s.currentSeat), MonoPhase.FINISHED.name))
        return ApplyResult.Success(ended, listOf(MonoEvent.MatchEnded(outcome(ended).ranking.map { it.seat.value })))
    }

    private fun expireMonoCall(s: MonoState, seat: SeatNumber, random: GameRandom): ApplyResult {
        if (s.monoWindowSeat != seat.value || s.monoCallWindowExpiry == 0L) {
            return ApplyResult.Success(s, emptyList())
        }
        val closed = s.copy(monoWindowSeat = null, monoCallWindowExpiry = 0)
        val penalty = s.monoRules.mandatory.monoPenaltyCards
        val drawn = drawCards(closed, penalty, random)
        val newHand = closed.handForSeat(seat.value) + drawn.cards
        return ApplyResult.Success(
            drawn.state.copy(hands = closed.hands + (seat.value to newHand)),
            listOf(MonoEvent.MonoMissed(seat, drawn.cards.size)),
        )
    }

    /** The Rule of 0: every hand moves one seat along the direction of play. */
    private fun passAllHands(s: MonoState, reversed: Boolean): MonoState {
        val active = activeSeats(s)
        if (active.size <= 1) return s
        val moved = active.associateWith { seat ->
            val giver = advance(s, seat, 1, !reversed)
            s.handForSeat(giver)
        }
        return s.copy(hands = s.hands + moved)
    }

    /** Seats still holding cards (finished seats are skipped). */
    private fun activeSeats(s: MonoState): List<Int> = s.seats.map { it.value }.filter { it !in s.finishOrder }

    private fun nextSeat(s: MonoState, from: Int): Int = advance(s, from, 1, s.reversed)

    /** [count] seats still in play on from [from] in the current direction (finished seats are passed over). */
    private fun advance(s: MonoState, from: Int, count: Int, reversed: Boolean): Int {
        val seats = s.seats.map { it.value }
        if (seats.isEmpty()) return from
        var index = seats.indexOf(from).takeIf { it >= 0 } ?: 0
        var moved = 0
        var guard = 0
        while (moved < count && guard < seats.size * (count + 1)) {
            index = if (reversed) (index - 1 + seats.size) % seats.size else (index + 1) % seats.size
            if (seats[index] !in s.finishOrder) moved++
            guard++
        }
        return seats[index]
    }

    /** The seat [seat] most wants to trade hands with (largest hand). */
    private fun swapPartner(s: MonoState, seat: Int): Int =
        activeSeats(s)
            .filter { it != seat }
            .maxByOrNull { s.cardCountForSeat(it) }
            ?: seat

    /** The colour the seat holds most of, so autoplay plays to its strength. */
    private fun bestColour(s: MonoState, seat: Int): MonoConstants.Color =
        MonoConstants.Color.entries.maxByOrNull { colour ->
            s.handForSeat(seat).count { it.playColor() == colour }
        } ?: MonoConstants.Color.RED

    /** The hand indexes matching [predicate], in hand order. */
    private inline fun List<MonoCard>.indexesOf(predicate: (MonoCard) -> Boolean): List<Int> =
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
