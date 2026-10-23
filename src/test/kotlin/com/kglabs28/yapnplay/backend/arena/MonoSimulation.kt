package com.kglabs28.yapnplay.backend.arena

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoAction
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoEvent
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoPhase
import com.kglabs28.yapnplay.backend.games.mono.domain.MonoState
import com.kglabs28.yapnplay.backend.games.mono.domain.rules.MonoRules
import com.kglabs28.yapnplay.backend.games.mono.engine.MonoEngine
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosAction
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosEvent
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosPhase
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState
import com.kglabs28.yapnplay.backend.games.monochaos.domain.rules.MonoChaosRules
import com.kglabs28.yapnplay.backend.games.monochaos.engine.MonoChaosEngine
import kotlin.random.Random

/**
 * Plays [games] whole MONO / Complete Chaos games with every seat on
 * autoplay, a random seat pressing CAUGHT now and then, and checks the
 * rules after every move:
 *  - no card is ever lost or made up (deck + discard + hands stay the same);
 *  - a wrong CAUGHT (nobody to catch) costs the presser the wrong-catch cards;
 *  - a taken stack adds exactly the stack's total to the taker's hand;
 *  - the Roulette flips one card at a time until the chosen colour, and stops on the first one;
 *  - Discard All dumps exactly its colour, and none of it stays in hand;
 *  - every game ends.
 * Returns a report: how often each thing happened, and every broken rule.
 */
class MonoSimulation(private val games: Int, private val seats: Int, private val catchOneIn: Int = CATCH_ONE_IN) {
    private val findings = mutableListOf<String>()
    private val counts = sortedMapOf<String, Int>()

    private fun count(what: String) {
        counts[what] = (counts[what] ?: 0) + 1
    }

    private fun broken(game: Int, step: Int, what: String) {
        if (findings.size < MAX_FINDINGS) findings += "game $game, move $step: $what"
    }

    fun mono(): String {
        val engine = MonoEngine()
        var lengths = 0L
        repeat(games) { game ->
            val random = SeededRandom(game.toLong())
            val dice = Random(game)
            var s = engine.start(MonoRules.TRADITIONAL, seats, random) as MonoState
            val total = s.drawPile.size + s.discardPile.size + s.hands.values.sumOf { it.size }
            var step = 0
            while (s.phase != MonoPhase.FINISHED && step++ < MAX_STEPS) {
                // Now and then a seat that isn't playing presses CAUGHT.
                if (catchOneIn > 0 && dice.nextInt(catchOneIn) == 0) {
                    val presser = s.seats.map { it.value }.filter { it != s.currentSeat && it !in s.finishOrder }.randomOrNull(dice)
                    if (presser != null) {
                        val before = s
                        val result = engine.apply(s, SeatNumber(presser), MonoAction.Caught, random)
                        if (result is ApplyResult.Success) {
                            val after = result.state as MonoState
                            result.events.forEach { e ->
                                when (e) {
                                    is MonoEvent.FalseCatch -> {
                                        count("wrong catch")
                                        val grew = after.cardCountForSeat(presser) - before.cardCountForSeat(presser)
                                        if (before.catchSeat == null && grew != before.monoRules.mandatory.falseCatchPenaltyCards && before.drawPile.size + before.discardPile.size > grew) {
                                            broken(game, step, "wrong catch gave $grew cards, not ${before.monoRules.mandatory.falseCatchPenaltyCards}")
                                        }
                                    }
                                    is MonoEvent.MonoCaught -> count("caught")
                                    else -> Unit
                                }
                            }
                            s = after
                        }
                    }
                }
                if (s.phase == MonoPhase.FINISHED) break
                val result = engine.autoplay(s, SeatNumber(s.currentSeat), random)
                if (result !is ApplyResult.Success) {
                    broken(game, step, "autoplay refused in ${s.phase}: $result")
                    break
                }
                result.events.forEach { e -> count(e::class.simpleName.orEmpty()) }
                s = result.state as MonoState
                // Most players call MONO when they get down to one card; some forget.
                s.catchSeat?.takeIf { it != s.monoCalledSeat && dice.nextInt(CALL_ONE_IN) != 0 }?.let { seat ->
                    (engine.apply(s, SeatNumber(seat), MonoAction.CallMono, random) as? ApplyResult.Success)?.let { count("MONO called"); s = it.state as MonoState }
                }
                val now = s.drawPile.size + s.discardPile.size + s.hands.values.sumOf { it.size }
                if (now != total) broken(game, step, "cards: $now on the table, should be $total")
            }
            if (s.phase != MonoPhase.FINISHED) broken(game, step, "never finished")
            lengths += step
        }
        return report("MONO", lengths)
    }

    fun chaos(): String {
        val engine = MonoChaosEngine()
        var lengths = 0L
        repeat(games) { game ->
            val random = SeededRandom(game.toLong())
            val dice = Random(game)
            var s = engine.start(MonoChaosRules.TRADITIONAL, seats, random) as MonoChaosState
            val total = s.drawPile.size + s.discardPile.size + s.hands.values.sumOf { it.size }
            var step = 0
            while (s.phase != MonoChaosPhase.FINISHED && step++ < MAX_STEPS) {
                if (catchOneIn > 0 && dice.nextInt(catchOneIn) == 0) {
                    val presser = s.seats.map { it.value }.filter { it != s.currentSeat && it !in s.finishOrder && !s.isEliminated(it) }.randomOrNull(dice)
                    if (presser != null) {
                        val before = s
                        val result = engine.apply(s, SeatNumber(presser), MonoChaosAction.Caught, random)
                        if (result is ApplyResult.Success) {
                            val after = result.state as MonoChaosState
                            result.events.forEach { e ->
                                when (e) {
                                    is MonoChaosEvent.FalseCatch -> {
                                        count("wrong catch")
                                        val grew = after.cardCountForSeat(presser) - before.cardCountForSeat(presser)
                                        val due = before.monoChaosRules.mandatory.falseCatchPenaltyCards
                                        if (before.catchSeat == null && grew != due && !after.isEliminated(presser) && before.drawPile.size + before.discardPile.size > grew) {
                                            broken(game, step, "wrong catch gave $grew cards, not $due")
                                        }
                                    }
                                    is MonoChaosEvent.MonoCaught -> count("caught")
                                    else -> Unit
                                }
                            }
                            s = after
                        }
                    }
                }
                // A catch can knock the last opponent out and end the match.
                if (s.phase == MonoChaosPhase.FINISHED) break
                val before = s
                val actor = s.currentSeat
                val result = engine.autoplay(s, SeatNumber(actor), random)
                if (result !is ApplyResult.Success) {
                    broken(game, step, "autoplay refused in ${s.phase}: $result")
                    break
                }
                s = result.state as MonoChaosState
                checkChaosMove(game, step, before, s, result.events.filterIsInstance<MonoChaosEvent>())
                s.catchSeat?.takeIf { it != s.monoCalledSeat && dice.nextInt(CALL_ONE_IN) != 0 }?.let { seat ->
                    (engine.apply(s, SeatNumber(seat), MonoChaosAction.CallMono, random) as? ApplyResult.Success)?.let { count("MONO called"); s = it.state as MonoChaosState }
                }
                val now = s.drawPile.size + s.discardPile.size + s.hands.values.sumOf { it.size }
                if (now != total) broken(game, step, "cards: $now on the table, should be $total")
            }
            if (s.phase != MonoChaosPhase.FINISHED) broken(game, step, "never finished")
            lengths += step
        }
        return report("Complete Chaos", lengths)
    }

    private fun checkChaosMove(game: Int, step: Int, before: MonoChaosState, after: MonoChaosState, events: List<MonoChaosEvent>) {
        events.forEach { e ->
            count(e::class.simpleName.orEmpty())
            when (e) {
                is MonoChaosEvent.StackAccepted -> {
                    val grew = after.cardCountForSeat(e.seat.value) - before.cardCountForSeat(e.seat.value)
                    if (!after.isEliminated(e.seat.value) && grew != before.activeStackPenalty) {
                        broken(game, step, "stack of ${before.activeStackPenalty} taken but the hand grew by $grew")
                    }
                }
                // The Roulette flips one card per move: it stops on the first card of its colour, never before.
                is MonoChaosEvent.CardDrawn -> if (before.phase == MonoChaosPhase.AWAITING_ROULETTE_DRAW) {
                    val color = before.rouletteColor
                    val stillFlipping = after.phase == MonoChaosPhase.AWAITING_ROULETTE_DRAW
                    if (e.card.playColor() == color) {
                        if (stillFlipping) broken(game, step, "roulette kept flipping past $color")
                        else if (after.topCardColor != color) broken(game, step, "roulette colour $color not live (${after.topCardColor})")
                    } else if (!stillFlipping && !after.isEliminated(e.seat.value) && after.drawPile.isNotEmpty()) {
                        broken(game, step, "roulette stopped before $color came")
                    }
                }
                is MonoChaosEvent.CardsDiscarded -> {
                    val played = events.filterIsInstance<MonoChaosEvent.CardPlayed>().lastOrNull()?.card as? MonoChaosCard.DiscardAllCard
                    if (played == null) {
                        broken(game, step, "cards dumped without a Discard All")
                    } else if (after.handForSeat(e.seat.value).any { it.playColor() == played.color }) {
                        broken(game, step, "Discard All left ${played.color} cards in hand")
                    }
                }
                else -> Unit
            }
        }
    }

    private fun report(name: String, lengths: Long): String = buildString {
        appendLine("$name, $games games of $seats players, average ${lengths / games.coerceAtLeast(1)} moves")
        appendLine("  happened: " + counts.entries.joinToString { "${it.key} ${it.value}" })
        if (findings.isEmpty()) appendLine("  no broken rules") else findings.forEach { appendLine("  BROKEN: $it") }
    }

    private companion object {
        const val MAX_STEPS = 20_000
        const val MAX_FINDINGS = 40
        /** By default, about one move in this many some seat presses CAUGHT (0 = never). */
        const val CATCH_ONE_IN = 60
        /** One player in this many forgets to call MONO at one card. */
        const val CALL_ONE_IN = 4
    }
}
