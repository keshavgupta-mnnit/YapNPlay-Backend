package com.kglabs28.yapnplay.backend.games.mono.engine

import com.kglabs28.yapnplay.backend.games.mono.domain.colourInPlay
import com.kglabs28.yapnplay.backend.games.mono.domain.heldBackByColour
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.BotDecision
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.Evaluation
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.isPlayableOn
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants.Color

/**
 * MONO's computer player. Every choice is a [BotDecision] valued as gain −
 * cost ([MonoBotTuning]): shedding a card (more so a high-value one when an
 * opponent is close to going out), hitting a next player who is close to
 * winning, keeping a playable colour, and the cost of spending a wild card
 * that could save a later turn. [misses] holds the colours each seat has
 * recently failed to play (they drew instead). MONO! is always called (the
 * session does it as soon as the window opens).
 */
class MonoBot(private val tuning: MonoBotTuning = MonoBotTuning(), private val trace: BotTrace? = null) : GameBot {

    override fun chooseAction(state: GameState, seat: SeatNumber): Action? = choose(state as MonoState, seat.value, emptyMap())

    fun choose(s: MonoState, me: Int, misses: Map<Int, Set<Color>>): Action? {
        if (s.currentSeat != me) return null
        return when (s.phase) {
            MonoPhase.AWAITING_PLAY -> PlayChoice(s, me).choose(trace)
            MonoPhase.AWAITING_COLOR_CHOICE -> ColourChoice(s, me, misses).choose(trace)?.let { MonoAction.ChooseColor(it) }
            MonoPhase.AWAITING_STACK_CHOICE -> StackChoice(s, me).choose(trace)
            MonoPhase.FINISHED -> null
        }
    }

    /** Play a card, draw, or (after a draw) pass. */
    inner class PlayChoice(private val s: MonoState, private val me: Int) : BotDecision<Action> {
        private val hand = s.handForSeat(me)
        private val top = s.discardPile.lastOrNull()
        private val threat = threatFrom(s.seats.map { it.value }.filter { it != me && it !in s.finishOrder }.minOfOrNull { s.cardCountForSeat(it) } ?: 7)
        private val next = nextSeat(s, me)

        override val name = "play"
        override fun label(option: Action) = when (option) {
            is MonoAction.PlayCard -> "play ${hand[option.handIndex].glyph()} ${hand[option.handIndex].colorName()}"
            else -> option.toString()
        }

        override fun options(): List<Action> {
            val noPowerFinish = s.monoRules.house.noPowerFinish
            // After a draw only the drawn card may go down, unless No Power Finish lets any legal card follow.
            s.drawnCardIndex?.takeIf { !noPowerFinish }?.let { drawn -> return listOf(MonoAction.Pass, MonoAction.PlayCard(drawn)) }
            val plays = hand.indices.filter {
                hand[it].isPlayableOn(top, s.topCardColor) && !(noPowerFinish && hand.size == 1 && hand[it] !is MonoCard.NumberCard) &&
                    !hand[it].heldBackByColour(hand, s.colourInPlay, s.monoRules.house.drawFourAnytime)
            }.map { MonoAction.PlayCard(it) }
            return plays + if (s.drawnCardIndex != null) MonoAction.Pass else MonoAction.DrawCard
        }

        override fun evaluate(option: Action): Evaluation = when (option) {
            is MonoAction.PlayCard -> playValue(hand[option.handIndex], hand.size - 1)
            MonoAction.DrawCard -> Evaluation(cost = tuning.cardCost.toDouble(), note = "one more card")
            else -> Evaluation(note = "keep the drawn card")
        }

        private fun playValue(card: MonoCard, left: Int): Evaluation {
            if (left == 0) return Evaluation.always("goes out")
            val rest = hand - card
            var gain = tuning.cardCost + points(card) * threat * tuning.shedPct / PERCENT
            var cost = 0.0
            val nextThreat = threatFrom(s.cardCountForSeat(next))
            gain += when (card) {
                is MonoCard.DrawTwoCard, MonoCard.PrismDrawFour -> card.penaltyValue() * tuning.attackValue * nextThreat
                is MonoCard.SkipCard, is MonoCard.ReverseCard -> SKIP_WORTH * tuning.attackValue * nextThreat
                else -> 0.0
            }
            // A wild can save any later turn: spending it now costs more when nobody is close to winning.
            if (card.isPrism()) cost += tuning.holdValue * (1 - threat)
            // Keep a colour this hand can follow.
            val colour = card.playColor() ?: bestColour(rest)
            gain += tuning.colourValue * rest.count { it.playColor() == colour || it.isPrism() } / (rest.size.coerceAtLeast(1)).toDouble() * COLOUR_SPREAD
            return Evaluation(gain = gain, cost = cost, note = "threat ${"%.1f".format(threat)}")
        }
    }

    /** The colour after a wild: what this hand holds, and what opponents (the next one most) just failed to play. */
    inner class ColourChoice(private val s: MonoState, private val me: Int, private val misses: Map<Int, Set<Color>>) : BotDecision<Color> {
        override val name = "colour"
        override fun options() = Color.entries.toList()
        override fun evaluate(option: Color): Evaluation {
            val mine = s.handForSeat(me).count { it.playColor() == option }
            val next = nextSeat(s, me)
            val blocked = misses.entries.filter { it.key != me && option in it.value }.sumOf { if (it.key == next) 2.0 else 1.0 }
            return Evaluation(gain = tuning.colourValue * mine + tuning.colourValue * MISS_SHARE * blocked, note = "$mine in hand")
        }
    }

    /** Stack a draw card onto the penalty (room house rule), or take it. */
    inner class StackChoice(private val s: MonoState, private val me: Int) : BotDecision<Action> {
        private val hand = s.handForSeat(me)
        override val name = "stack ${s.activeStackPenalty}"
        override fun options(): List<Action> = listOf<Action>(MonoAction.AcceptStack) + hand.indices.filter {
            hand[it].penaltyValue() >= s.stackTopValue && hand[it].penaltyValue() > 0 && hand[it].isPlayableOn(s.discardPile.lastOrNull(), s.topCardColor)
        }.map { MonoAction.PlayCard(it) }

        override fun evaluate(option: Action): Evaluation = when (option) {
            MonoAction.AcceptStack -> Evaluation(cost = s.activeStackPenalty * tuning.cardCost.toDouble(), note = "draw ${s.activeStackPenalty}")
            is MonoAction.PlayCard -> Evaluation(gain = tuning.cardCost.toDouble(), cost = tuning.holdValue * hand[option.handIndex].penaltyValue() / FOUR, note = "pass it on")
            else -> Evaluation.never("not here")
        }
    }


    private fun bestColour(cards: List<MonoCard>): Color = Color.entries.maxByOrNull { c -> cards.count { it.playColor() == c } } ?: Color.RED

    /** The next seat still holding cards. */
    private fun nextSeat(s: MonoState, from: Int): Int {
        val seats = s.seats.map { it.value }
        var i = seats.indexOf(from).coerceAtLeast(0)
        repeat(seats.size) {
            i = ((if (s.reversed) i - 1 else i + 1) % seats.size + seats.size) % seats.size
            if (seats[i] !in s.finishOrder) return seats[i]
        }
        return seats[i]
    }

    companion object {
        private const val PERCENT = 100.0
        private const val SKIP_WORTH = 2
        private const val FOUR = 4.0
        private const val COLOUR_SPREAD = 3
        private const val MISS_SHARE = 0.6

        /** How close a seat holding [cards] is to going out: 1 at one or two cards, less with more. */
        fun threatFrom(cards: Int): Double = when {
            cards <= 2 -> 1.0
            cards <= 4 -> 0.5
            else -> 0.2
        }

        /** Card points (the usual scoring): numbers at face value, actions 20, wilds 50. */
        fun points(card: MonoCard): Int = when (card) {
            is MonoCard.NumberCard -> card.number
            MonoCard.PrismColorWild, MonoCard.PrismDrawFour -> 50
            else -> 20
        }
    }
}

/**
 * MONO bot values (runtime settings mono.bot_*): a card in hand costs
 * [cardCost]; shedding a card's points counts [shedPct] % × how close an
 * opponent is to winning; each penalty card on a next player close to
 * winning is worth [attackValue]; a wild held back is worth [holdValue];
 * keeping a playable colour is worth [colourValue]; a knockout (Chaos)
 * costs [knockoutCost].
 */
data class MonoBotTuning(
    val cardCost: Int = 10,
    val shedPct: Int = 100,
    val attackValue: Int = 8,
    val holdValue: Int = 15,
    val colourValue: Int = 10,
    val knockoutCost: Int = 1_000,
)
