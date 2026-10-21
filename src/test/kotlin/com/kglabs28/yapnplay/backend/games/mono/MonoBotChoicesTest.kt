package com.kglabs28.yapnplay.backend.games.mono

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoTurnState
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoRules
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoBot
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosTurnState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosBot
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/** MONO bots choosing like a player would. */
class MonoBotChoicesTest {
    private val bot = MonoBot()
    private val chaosBot = MonoChaosBot()
    private val R = MonoConstants.Color.RED

    private fun standard(mine: List<MonoCard>, others: List<Int>, top: MonoCard = MonoCard.NumberCard(R, 5), phase: MonoPhase = MonoPhase.AWAITING_PLAY): MonoState {
        val hands = mapOf(1 to mine) + others.withIndex().associate { (i, n) -> i + 2 to List(n) { MonoCard.NumberCard(MonoConstants.Color.BLUE, it % 9 + 1) } }
        return MonoState(
            GameId("mono"), MonoRules.TRADITIONAL, hands.keys.sorted().map(::SeatNumber), MonoTurnState(SeatNumber(1), phase.name),
            hands, List(40) { MonoCard.NumberCard(MonoConstants.Color.GOLD, it % 9 + 1) }, listOf(top), 1, phase, top.playColor(),
        )
    }

    private fun chaos(mine: List<MonoChaosCard>, others: List<Int>, top: MonoChaosCard = MonoChaosCard.NumberCard(Color.RED, 5), phase: MonoChaosPhase = MonoChaosPhase.AWAITING_PLAY, stack: Int = 0): MonoChaosState {
        val hands = mapOf(1 to mine) + others.withIndex().associate { (i, n) -> i + 2 to List(n) { MonoChaosCard.NumberCard(Color.BLUE, it % 9 + 1) } }
        return MonoChaosState(
            GameId("mono-chaos"), MonoChaosRules.TRADITIONAL, hands.keys.sorted().map(::SeatNumber), MonoChaosTurnState(SeatNumber(1), phase.name),
            hands, List(60) { MonoChaosCard.NumberCard(Color.GOLD, it % 9 + 1) }, listOf(top), 1, phase, top.playColor(),
            activeStackPenalty = stack, stackTopValue = stack,
        )
    }

    @Test
    fun `with a rival on one card it sheds its high card first`() {
        val s = standard(listOf(MonoCard.NumberCard(R, 1), MonoCard.NumberCard(R, 9), MonoCard.NumberCard(MonoConstants.Color.GREEN, 4)), others = listOf(1, 7))
        assertEquals(MonoAction.PlayCard(1), bot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `nobody close to winning - it keeps the wild and plays the matching number`() {
        val s = standard(listOf(MonoCard.PrismColorWild, MonoCard.NumberCard(R, 3), MonoCard.NumberCard(MonoConstants.Color.GREEN, 4)), others = listOf(7, 7))
        assertEquals(MonoAction.PlayCard(1), bot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `after a wild it names the colour it holds most of`() {
        val s = standard(
            listOf(MonoCard.NumberCard(MonoConstants.Color.GOLD, 2), MonoCard.NumberCard(MonoConstants.Color.GOLD, 6), MonoCard.NumberCard(R, 1)),
            others = listOf(5), top = MonoCard.PrismColorWild, phase = MonoPhase.AWAITING_COLOR_CHOICE,
        )
        assertEquals(MonoAction.ChooseColor(MonoConstants.Color.GOLD), bot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `near the knockout it stacks rather than take the pile`() {
        val hand = List(20) { MonoChaosCard.NumberCard(Color.GREEN, it % 9 + 1) } + MonoChaosCard.PrismDrawTen
        val s = chaos(hand, others = listOf(5, 5), top = MonoChaosCard.PrismDrawSix, phase = MonoChaosPhase.AWAITING_STACK_CHOICE, stack = 6)
        assertEquals(MonoChaosAction.StackDraw(20), chaosBot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `with a small hand it takes a +2 instead of spending its +10`() {
        val hand = listOf(MonoChaosCard.NumberCard(Color.GREEN, 3), MonoChaosCard.NumberCard(Color.GREEN, 4), MonoChaosCard.PrismDrawTen)
        val s = chaos(hand, others = listOf(6, 6), top = MonoChaosCard.DrawTwoCard(Color.RED), phase = MonoChaosPhase.AWAITING_STACK_CHOICE, stack = 2)
        assertEquals(MonoChaosAction.AcceptStack, chaosBot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `a 7 trades with the rival who is about to win`() {
        val s = chaos(List(6) { MonoChaosCard.NumberCard(Color.BLUE, it) } + MonoChaosCard.NumberCard(Color.RED, 7), others = listOf(8, 1, 5), phase = MonoChaosPhase.AWAITING_SWAP_CHOICE)
        assertEquals(MonoChaosAction.ChooseSwapTarget(3), chaosBot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `as the Roulette victim it names the colour with the most cards still unseen`() {
        // It holds lots of red, so red is the rarest left in the deck: pick another colour.
        val hand = List(8) { MonoChaosCard.NumberCard(Color.RED, it) } + List(2) { MonoChaosCard.NumberCard(Color.BLUE, it) } + List(2) { MonoChaosCard.NumberCard(Color.GREEN, it) }
        val s = chaos(hand, others = listOf(5), top = MonoChaosCard.PrismColorRoulette, phase = MonoChaosPhase.AWAITING_ROULETTE_COLOR)
        assertEquals(MonoChaosAction.ChooseRouletteColor(Color.GOLD), chaosBot.choose(s, 1, emptyMap()))
    }

    @Test
    fun `Discard All goes when it clears a lot of one colour`() {
        val hand = listOf(MonoChaosCard.NumberCard(Color.RED, 2)) + MonoChaosCard.DiscardAllCard(Color.RED) + List(4) { MonoChaosCard.NumberCard(Color.RED, it + 3) } + MonoChaosCard.NumberCard(Color.BLUE, 1)
        val s = chaos(hand, others = listOf(6, 6))
        assertEquals(MonoChaosAction.PlayCard(1), chaosBot.choose(s, 1, emptyMap()))
    }
}
