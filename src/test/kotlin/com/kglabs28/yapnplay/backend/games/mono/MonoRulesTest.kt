package com.kglabs28.yapnplay.backend.games.mono

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoCard
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.buildMonoDeck
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoRules
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoEngine
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosEvent
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosTurnState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.buildMonoChaosDeck
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosEngine
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants.Color
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The MONO rules as published: decks, the first card, stacking, 2-player Wild Reverse +4, knockouts, Discard All. */
class MonoRulesTest {
    private val chaos = MonoChaosEngine()

    private class Seeded(seed: Int) : GameRandom {
        private val random = Random(seed)
        override fun nextInt(bound: Int) = random.nextInt(bound)
    }

    /** A Chaos table: seat 1 to act in [phase]; [hands] seat → cards; red [top] on the pile. */
    private fun table(
        hands: Map<Int, List<MonoChaosCard>>,
        top: MonoChaosCard = MonoChaosCard.NumberCard(Color.RED, 5),
        phase: MonoChaosPhase = MonoChaosPhase.AWAITING_PLAY,
        stack: Int = 0,
        draw: List<MonoChaosCard> = List(60) { MonoChaosCard.NumberCard(Color.BLUE, it % 10) },
    ) = MonoChaosState(
        GameId("mono-chaos"), MonoChaosRules.TRADITIONAL, hands.keys.sorted().map(::SeatNumber),
        MonoChaosTurnState(SeatNumber(1), phase.name), hands, draw, listOf(top), 1, phase, top.playColor(),
        activeStackPenalty = stack, stackTopValue = stack,
    )

    private fun play(s: MonoChaosState, seat: Int, action: MonoChaosAction) =
        assertIs<ApplyResult.Success>(chaos.apply(s, SeatNumber(seat), action, Seeded(1)))

    @Test
    fun `the decks have the published cards`() {
        val standard = buildMonoDeck()
        assertEquals(108, standard.size)
        assertEquals(4, standard.count { it is MonoCard.NumberCard && it.number == 0 })
        assertEquals(8, standard.count { it is MonoCard.DrawTwoCard })
        assertEquals(4, standard.count { it == MonoCard.PrismDrawFour })
        val deck = buildMonoChaosDeck()
        assertEquals(168, deck.size)
        assertEquals(80, deck.count { it is MonoChaosCard.NumberCard })
        assertEquals(12, deck.count { it is MonoChaosCard.DrawTwoCard })
        assertEquals(8, deck.count { it == MonoChaosCard.PrismReverseDrawFour })
        assertEquals(8, deck.count { it == MonoChaosCard.PrismColorRoulette })
    }

    @Test
    fun `the first card is always a number card`() {
        repeat(50) { seed ->
            val s = MonoEngine().start(MonoRules.TRADITIONAL, 4, Seeded(seed)) as MonoState
            assertIs<MonoCard.NumberCard>(s.discardPile.last())
            val c = chaos.start(MonoChaosRules.TRADITIONAL, 4, Seeded(seed)) as MonoChaosState
            assertIs<MonoChaosCard.NumberCard>(c.discardPile.last())
        }
    }

    @Test
    fun `any draw card of equal or higher value stacks, whatever its colour`() {
        val s = table(
            mapOf(1 to listOf(MonoChaosCard.ColoredDrawFourCard(Color.BLUE), MonoChaosCard.DrawTwoCard(Color.GREEN), MonoChaosCard.PrismColorRoulette, MonoChaosCard.NumberCard(Color.RED, 1)), 2 to listOf(MonoChaosCard.NumberCard(Color.RED, 2)), 3 to listOf(MonoChaosCard.NumberCard(Color.RED, 3))),
            top = MonoChaosCard.ColoredDrawFourCard(Color.RED), phase = MonoChaosPhase.AWAITING_STACK_CHOICE, stack = 4,
        )
        val stacked = play(s, 1, MonoChaosAction.StackDraw(0))
        assertEquals(8, (stacked.state as MonoChaosState).activeStackPenalty, "a blue +4 on a red +4")
        assertIs<ApplyResult.Rejected>(chaos.apply(s, SeatNumber(1), MonoChaosAction.StackDraw(1), Seeded(1)), "a +2 can't go on a +4")
        assertIs<ApplyResult.Rejected>(chaos.apply(s, SeatNumber(1), MonoChaosAction.StackDraw(2), Seeded(1)), "the Roulette can't be stacked")
    }

    @Test
    fun `with two players a Wild Reverse +4 comes back to the one who played it`() {
        val s = table(mapOf(1 to listOf(MonoChaosCard.PrismReverseDrawFour, MonoChaosCard.NumberCard(Color.RED, 1), MonoChaosCard.NumberCard(Color.RED, 2)), 2 to List(5) { MonoChaosCard.NumberCard(Color.GREEN, it) }))
        val played = play(s, 1, MonoChaosAction.PlayCard(0)).state as MonoChaosState
        val chosen = play(played, 1, MonoChaosAction.ChooseColor(Color.GOLD)).state as MonoChaosState
        assertEquals(MonoChaosPhase.AWAITING_STACK_CHOICE, chosen.phase)
        assertEquals(1, chosen.currentSeat)
        assertEquals(4, chosen.activeStackPenalty)
    }

    @Test
    fun `a 0 that hands someone 25 cards knocks them out`() {
        val big = List(24) { MonoChaosCard.NumberCard(Color.BLUE, it % 10) }
        val s = table(mapOf(1 to big + MonoChaosCard.NumberCard(Color.RED, 0), 2 to List(3) { MonoChaosCard.NumberCard(Color.GREEN, it) }, 3 to List(3) { MonoChaosCard.NumberCard(Color.GOLD, it) }))
        // Seat 1's 24 remaining cards go to seat 2: still under 25. Make it 25 with one more.
        val heavy = s.copy(hands = s.hands + (1 to big + MonoChaosCard.NumberCard(Color.BLUE, 9) + MonoChaosCard.NumberCard(Color.RED, 0)))
        val result = play(heavy, 1, MonoChaosAction.PlayCard(25))
        assertTrue(result.events.any { it is MonoChaosEvent.HandsPassed })
        assertTrue(2 in (result.state as MonoChaosState).eliminatedSeats, "seat 2 now holds 25")
    }

    @Test
    fun `Discard All clears the colour, and clearing the whole hand wins`() {
        val s = table(mapOf(1 to listOf(MonoChaosCard.DiscardAllCard(Color.RED), MonoChaosCard.NumberCard(Color.RED, 3), MonoChaosCard.SkipCard(Color.RED)), 2 to List(3) { MonoChaosCard.NumberCard(Color.GREEN, it) }))
        val result = play(s, 1, MonoChaosAction.PlayCard(0))
        assertTrue(result.events.contains(MonoChaosEvent.CardsDiscarded(SeatNumber(1), 2)))
        assertEquals(MonoChaosPhase.FINISHED, (result.state as MonoChaosState).phase)
    }

    @Test
    fun `a 7 played as the last card wins before any swap`() {
        val s = table(mapOf(1 to listOf(MonoChaosCard.NumberCard(Color.RED, 7)), 2 to List(3) { MonoChaosCard.NumberCard(Color.GREEN, it) }))
        assertEquals(MonoChaosPhase.FINISHED, (play(s, 1, MonoChaosAction.PlayCard(0)).state as MonoChaosState).phase)
    }

    @Test
    fun `a modified client can't draw or pass its way out of a penalty`() {
        val chaosStack = table(
            mapOf(1 to listOf(MonoChaosCard.NumberCard(Color.RED, 1), MonoChaosCard.NumberCard(Color.RED, 2)), 2 to List(3) { MonoChaosCard.NumberCard(Color.GREEN, it) }),
            top = MonoChaosCard.DrawTwoCard(Color.RED), phase = MonoChaosPhase.AWAITING_STACK_CHOICE, stack = 2,
        )
        for (cheat in listOf(MonoChaosAction.DrawUntilPlayable, MonoChaosAction.Pass, MonoChaosAction.ChooseColor(Color.GOLD), MonoChaosAction.ChooseRouletteColor(Color.GOLD), MonoChaosAction.ChooseSwapTarget(2))) {
            val r = assertIs<ApplyResult.Rejected>(chaos.apply(chaosStack, SeatNumber(1), cheat, Seeded(1)), "$cheat under a stack")
            assertEquals("BAD_PHASE", r.rejection.code)
        }
        val playing = table(mapOf(1 to listOf(MonoChaosCard.NumberCard(Color.RED, 1), MonoChaosCard.NumberCard(Color.RED, 2)), 2 to List(3) { MonoChaosCard.NumberCard(Color.GREEN, it) }))
        for (cheat in listOf(MonoChaosAction.AcceptStack, MonoChaosAction.Pass, MonoChaosAction.StackDraw(0))) {
            assertEquals("BAD_PHASE", assertIs<ApplyResult.Rejected>(chaos.apply(playing, SeatNumber(1), cheat, Seeded(1)), "$cheat with no stack").rejection.code)
        }

        val standard = MonoEngine()
        val s0 = standard.start(MonoRules.TRADITIONAL, 2, Seeded(4)) as MonoState
        val stacked = s0.copy(phase = com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase.AWAITING_STACK_CHOICE, activeStackPenalty = 2, stackTopValue = 2)
        val seat = SeatNumber(s0.currentSeat)
        for (cheat in listOf(com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction.DrawCard, com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction.Pass)) {
            assertEquals("BAD_PHASE", assertIs<ApplyResult.Rejected>(standard.apply(stacked, seat, cheat, Seeded(1)), "$cheat under a stack").rejection.code)
        }
        assertEquals("BAD_PHASE", assertIs<ApplyResult.Rejected>(standard.apply(s0, seat, com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction.Pass, Seeded(1)), "a free skip").rejection.code)
    }
}
