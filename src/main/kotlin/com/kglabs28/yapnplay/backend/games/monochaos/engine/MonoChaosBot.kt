package com.kglabs28.yapnplay.backend.games.monochaos.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.BotDecision
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.Evaluation
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.base.play.Standing
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoBot
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoBotTuning
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.isPlayableOn
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants.Color

/**
 * MONO: Complete Chaos's computer player. Like [MonoBot] (gain − cost −
 * risk per option), plus: draw cards are held as stack defence; stacking
 * is weighed against taking the pile, which near the knockout threshold
 * means elimination; a 7 trades with the opponent closest to winning; a 0
 * is worth the hand that would come in; Discard All clears the colour it
 * holds most of; as the Roulette victim it names the colour with the most
 * cards still unseen (the fewest flips). Risk counts × the standing factor
 * (fewer cards than the rest → more careful). MONO! is always called.
 */
class MonoChaosBot(private val tuning: MonoBotTuning = MonoBotTuning(), private val trace: BotTrace? = null) : GameBot {

    override fun chooseAction(state: GameState, seat: SeatNumber): Action? = choose(state as MonoChaosState, seat.value, emptyMap())

    fun choose(s: MonoChaosState, me: Int, misses: Map<Int, Set<Color>>): Action? {
        if (s.currentSeat != me) return null
        return when (s.phase) {
            MonoChaosPhase.AWAITING_PLAY, MonoChaosPhase.AWAITING_DRAW_UNTIL_PLAYABLE -> PlayChoice(s, me).choose(trace)
            MonoChaosPhase.AWAITING_COLOR_CHOICE -> ColourChoice(s, me, misses).choose(trace)?.let { MonoChaosAction.ChooseColor(it) }
            MonoChaosPhase.AWAITING_ROULETTE_COLOR -> RouletteChoice(s, me).choose(trace)?.let { MonoChaosAction.ChooseRouletteColor(it) }
            MonoChaosPhase.AWAITING_STACK_CHOICE -> StackChoice(s, me).choose(trace)
            MonoChaosPhase.AWAITING_SWAP_CHOICE -> SwapChoice(s, me).choose(trace)?.let { MonoChaosAction.ChooseSwapTarget(it) }
            MonoChaosPhase.FINISHED -> null
        }
    }

    private fun active(s: MonoChaosState) = s.seats.map { it.value }.filter { !s.isEliminated(it) }
    private fun threshold(s: MonoChaosState) = s.monoChaosRules.mandatory.knockoutThreshold

    /** Fewer cards than the rest → more careful (risk counts more). */
    private fun riskWeight(s: MonoChaosState, me: Int): Double {
        val limit = threshold(s).toDouble()
        val best = active(s).maxOf { limit - s.cardCountForSeat(it) }
        return Standing.factor(limit - s.cardCountForSeat(me), best, STANDING_MIN, STANDING_MAX)
    }

    /** Cost of ending up with [cards]: the cards themselves, and elimination at the knockout threshold. */
    private fun handCost(s: MonoChaosState, cards: Int): Double =
        cards * tuning.cardCost + if (s.monoChaosRules.house.knockoutEnabled && cards >= threshold(s)) tuning.knockoutCost.toDouble() else 0.0

    /** Play a card, draw until something plays, or (after a draw) pass. */
    inner class PlayChoice(private val s: MonoChaosState, private val me: Int) : BotDecision<Action> {
        private val hand = s.handForSeat(me)
        private val top = s.discardPile.lastOrNull()
        private val others = active(s).filter { it != me }
        private val threat = MonoBot.threatFrom(others.minOfOrNull { s.cardCountForSeat(it) } ?: 7)
        private val next = step(s, me, 1)

        override val name = "play"
        override val riskWeight = riskWeight(s, me)
        override fun label(option: Action) = when (option) {
            is MonoChaosAction.PlayCard -> "play ${hand[option.handIndex].glyph()} ${hand[option.handIndex].colorName()}"
            else -> option.toString()
        }

        override fun options(): List<Action> {
            s.drawnCardIndex?.let { drawn -> return listOf(MonoChaosAction.Pass, MonoChaosAction.PlayCard(drawn)) }
            val plays = hand.indices.filter { hand[it].isPlayableOn(top, s.topCardColor) }.map { MonoChaosAction.PlayCard(it) }
            return plays + MonoChaosAction.DrawUntilPlayable
        }

        override fun evaluate(option: Action): Evaluation = when (option) {
            is MonoChaosAction.PlayCard -> playValue(hand[option.handIndex])
            MonoChaosAction.DrawUntilPlayable -> {
                // Drawing until something plays: about 2–3 cards, and a knockout if the hand is already big.
                val expected = hand.size + EXPECTED_DRAWS
                Evaluation(cost = EXPECTED_DRAWS * tuning.cardCost, risk = handCost(s, expected.toInt()) - expected * tuning.cardCost, note = "draw ~${EXPECTED_DRAWS.toInt()}")
            }
            else -> Evaluation(note = "keep the drawn card")
        }

        private fun playValue(card: MonoChaosCard): Evaluation {
            val rest = hand - card
            if (rest.isEmpty()) return Evaluation.always("goes out")
            var gain = tuning.cardCost + points(card) * threat * tuning.shedPct / PERCENT
            var cost = 0.0
            var note = ""
            val nextThreat = MonoBot.threatFrom(s.cardCountForSeat(next))
            when (card) {
                is MonoChaosCard.NumberCard -> when {
                    card.number == SEVEN && s.monoChaosRules.house.sevenSwap && others.isNotEmpty() -> {
                        val best = others.maxOf { swapValue(s, rest.size, it) }
                        gain += best
                        note = "swap ${best.toInt()}"
                    }
                    card.number == ZERO && s.monoChaosRules.house.zeroPass -> {
                        // My hand moves on; the previous player's comes to me.
                        val incoming = s.cardCountForSeat(step(s, me, -1))
                        gain += (rest.size - incoming) * tuning.cardCost
                        note = "pass for $incoming"
                    }
                }
                is MonoChaosCard.DiscardAllCard -> {
                    val same = rest.count { it.playColor() == card.color }
                    gain += same * (tuning.cardCost + tuning.colourValue)
                    if (same == rest.size) return Evaluation.always("goes out")
                    note = "clears $same"
                }
                is MonoChaosCard.SkipEveryoneCard -> gain += EXTRA_TURN * tuning.cardCost
                is MonoChaosCard.SkipCard, is MonoChaosCard.ReverseCard -> gain += SKIP_WORTH * tuning.attackValue * nextThreat
                MonoChaosCard.PrismColorRoulette -> gain += ROULETTE_DRAWS * tuning.attackValue * (nextThreat + ATTACK_FLOOR)
                else -> Unit
            }
            if (card.penaltyValue() > 0) {
                gain += card.penaltyValue() * tuning.attackValue * (nextThreat + ATTACK_FLOOR)
                // A draw card held back answers the next stack.
                cost += tuning.holdValue * card.penaltyValue() / FOUR * (1 - threat)
            } else if (card.isPrism()) {
                cost += tuning.holdValue * (1 - threat)
            }
            val colour = card.playColor() ?: bestColour(rest)
            gain += tuning.colourValue * rest.count { it.playColor() == colour || it.isPrism() } / rest.size.toDouble() * COLOUR_SPREAD
            return Evaluation(gain = gain, cost = cost, note = note)
        }
    }

    /** Stack a draw card of equal or higher value (any colour), or take the pile. */
    inner class StackChoice(private val s: MonoChaosState, private val me: Int) : BotDecision<Action> {
        private val hand = s.handForSeat(me)
        private val total = s.activeStackPenalty
        override val name = "stack $total"
        override val riskWeight = riskWeight(s, me)
        override fun options(): List<Action> = listOf<Action>(MonoChaosAction.AcceptStack) +
            hand.indices.filter { hand[it].penaltyValue() > 0 && hand[it].penaltyValue() >= s.stackTopValue }.map { MonoChaosAction.StackDraw(it) }

        override fun evaluate(option: Action): Evaluation = when (option) {
            MonoChaosAction.AcceptStack -> Evaluation(
                cost = total * tuning.cardCost.toDouble(),
                risk = handCost(s, hand.size + total) - (hand.size + total) * tuning.cardCost,
                note = "take $total (${hand.size + total} in hand)",
            )
            is MonoChaosAction.StackDraw -> {
                val card = hand[option.handIndex]
                val newTotal = total + card.penaltyValue()
                // With two players it comes straight back; otherwise it may come round again.
                val back = if (active(s).size <= 2) TWO_PLAYER_RETURN else RETURN_CHANCE
                Evaluation(
                    gain = tuning.cardCost.toDouble(),
                    cost = tuning.holdValue * card.penaltyValue() / FOUR,
                    risk = back * (handCost(s, hand.size - 1 + newTotal) - (hand.size - 1) * tuning.cardCost),
                    note = "+${card.penaltyValue()} → $newTotal",
                )
            }
            else -> Evaluation.never("not here")
        }
    }

    inner class ColourChoice(private val s: MonoChaosState, private val me: Int, private val misses: Map<Int, Set<Color>>) : BotDecision<Color> {
        override val name = "colour"
        override fun options() = Color.entries.toList()
        override fun evaluate(option: Color): Evaluation {
            val mine = s.handForSeat(me).count { it.playColor() == option }
            val next = step(s, me, 1)
            val blocked = misses.entries.filter { it.key != me && option in it.value }.sumOf { if (it.key == next) 2.0 else 1.0 }
            return Evaluation(gain = tuning.colourValue * mine + tuning.colourValue * MISS_SHARE * blocked, note = "$mine in hand")
        }
    }

    /** As the Roulette victim: the colour with the most cards still unseen, so the fewest flips. */
    inner class RouletteChoice(private val s: MonoChaosState, private val me: Int) : BotDecision<Color> {
        override val name = "roulette"
        override fun options() = Color.entries.toList()
        override fun evaluate(option: Color): Evaluation {
            val seen = s.handForSeat(me) + s.discardPile
            val unseenTotal = (DECK_SIZE - seen.size).coerceAtLeast(1)
            val unseenOfColour = (PER_COLOUR - seen.count { it.playColor() == option }).coerceAtLeast(1)
            val flips = unseenTotal.toDouble() / unseenOfColour
            return Evaluation(cost = flips * tuning.cardCost, note = "~${"%.1f".format(flips)} flips")
        }
    }

    /** The Rule of 7: the opponent whose hand is smallest, most so if they are about to win. */
    inner class SwapChoice(private val s: MonoChaosState, private val me: Int) : BotDecision<Int> {
        override val name = "swap"
        override fun options() = active(s).filter { it != me }
        override fun evaluate(option: Int) = Evaluation(gain = swapValue(s, s.cardCountForSeat(me), option), note = "${s.cardCountForSeat(option)} cards")
        override val tieBreak: Comparator<Int> = compareBy { it }
    }

    private fun swapValue(s: MonoChaosState, mine: Int, target: Int): Double {
        val theirs = s.cardCountForSeat(target)
        return (mine - theirs) * tuning.cardCost + MonoBot.threatFrom(theirs) * tuning.attackValue * FOUR
    }

    private fun bestColour(cards: List<MonoChaosCard>): Color = Color.entries.maxByOrNull { c -> cards.count { it.playColor() == c } } ?: Color.RED

    /** [steps] active seats on from [from] in the direction of play (negative: backwards). */
    private fun step(s: MonoChaosState, from: Int, steps: Int): Int {
        val seats = active(s)
        if (seats.isEmpty()) return from
        val i = seats.indexOf(from).coerceAtLeast(0)
        val d = if (s.reversed) -steps else steps
        return seats[((i + d) % seats.size + seats.size) % seats.size]
    }

    private fun points(card: MonoChaosCard): Int = when (card) {
        is MonoChaosCard.NumberCard -> card.number
        else -> if (card.isPrism()) PRISM_POINTS else ACTION_POINTS
    }

    private companion object {
        const val PERCENT = 100.0
        const val SEVEN = 7
        const val ZERO = 0
        const val FOUR = 4.0
        const val SKIP_WORTH = 2
        const val EXTRA_TURN = 1.5
        const val ROULETTE_DRAWS = 4
        const val ATTACK_FLOOR = 0.2
        const val COLOUR_SPREAD = 3
        const val MISS_SHARE = 0.6
        const val EXPECTED_DRAWS = 2.5
        const val RETURN_CHANCE = 0.2
        const val TWO_PLAYER_RETURN = 1.0
        const val STANDING_MIN = 0.6
        const val STANDING_MAX = 1.4
        const val DECK_SIZE = 168
        const val PER_COLOUR = 36
        const val PRISM_POINTS = 50
        const val ACTION_POINTS = 20
    }
}
