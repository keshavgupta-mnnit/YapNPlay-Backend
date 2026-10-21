package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.BotDecision
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.Evaluation
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.base.play.Standing
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.PieceId
import com.kglabs28.yapnplay.backend.games.royalludo.utils.DICE_FACES
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PIECES_PER_COLOUR
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_ENTER_HOME
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_FINISH
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PROGRESS_IN_BASE
import com.kglabs28.yapnplay.backend.games.royalludo.utils.RoyalLudoBoardUtils
import com.kglabs28.yapnplay.backend.games.royalludo.utils.TRACK_SIZE

/**
 * Royal Ludo's computer player. It rolls when it must roll; to move, it
 * values every legal piece ([MoveChoice]): gain = squares advanced (none
 * for shuffling inside the home lane, which is already safe), a capture
 * (the victim's worth, more against a rival close to finishing), finishing,
 * leaving base (more early, when another piece out means more choices on
 * later rolls), the extra turns the rules give, and pieces just ahead it
 * could hit next. Risk = the change in expected loss to hits (an opponent
 * behind within one roll, two with a six, or a six from base), × the
 * standing factor (behind → bolder, leading → careful). A pair only
 * protects when the match's pair-protection rule is on, and is never a
 * goal in itself. Decisions go to [trace] for the admin page.
 */
class RoyalLudoBot(
    private val tuning: RoyalLudoBotTuning = RoyalLudoBotTuning(),
    private val trace: BotTrace? = null,
) : GameBot {

    override fun chooseAction(state: GameState, seat: SeatNumber): Action? {
        val s = state as? RoyalLudoState ?: return null
        if (s.currentSeat != seat.value) return null
        return when (s.phase) {
            Phase.AWAITING_ROLL -> RollDice
            Phase.AWAITING_MOVE -> bestPiece(s)?.let { MovePiece(PieceId(it.toString())) }
            Phase.FINISHED -> null
        }
    }

    /** The legal piece worth moving most. */
    fun bestPiece(state: RoyalLudoState): Int? = MoveChoice(state).choose(trace)

    /** Every legal piece's gain and risk with the current roll. */
    inner class MoveChoice(private val s: RoyalLudoState) : BotDecision<Int> {
        private val seat = s.currentSeat
        private val colour = s.colourForSeat(seat)
        private val roll = s.lastRoll ?: 0
        private val house = s.ludoRules.house
        private val pairs = house.pairProtection
        private val mine = s.piecesForSeat(seat)

        override val name = "move with $roll"
        override fun options(): List<Int> = s.legalPieces
        override fun label(option: Int) = "piece $option (${mine[option]})"

        override val riskWeight: Double = run {
            val scores = s.seats.map { it.value }.filter { it !in s.removalOrder }.associateWith { total(it) }
            val standing = Standing.factor(scores[seat] ?: 0.0, scores.values.maxOrNull() ?: 0.0, tuning.standingMinPct / PERCENT, tuning.standingMaxPct / PERCENT)
            tuning.riskWeightPct / PERCENT * standing
        }

        override fun evaluate(option: Int): Evaluation {
            val from = mine[option]
            val to = if (from == PROGRESS_IN_BASE) 0 else from + roll
            val toSquare = squareOrNull(colour, to)
            val victims = toSquare?.takeUnless { RoyalLudoBoardUtils.isSafeSquare(it) }?.let { capturable(it) }.orEmpty()
            val after = mine.toMutableList().also { it[option] = to }
            val gone = victims.map { it.colour to it.progress }.toSet()

            // Inside the home lane a piece is already safe: moving it only matters to finish.
            var gain = if (from > PROGRESS_ENTER_HOME && to != PROGRESS_FINISH) 0.0 else (to - from.coerceAtLeast(0)).toDouble()
            var why = ""
            if (victims.isNotEmpty()) {
                gain += victims.sumOf { v -> worth(v.progress) * if (v.leader) tuning.endgameCapturePct / PERCENT else 1.0 } * tuning.captureWeightPct / PERCENT
                if (house.extraTurnOnCapture) gain += tuning.extraTurnBonus
                why += "capture "
            }
            if (to == PROGRESS_FINISH) {
                gain += tuning.finishBonus
                if (house.extraTurnOnHome) gain += tuning.extraTurnBonus
                why += "finish "
            }
            if (from == PROGRESS_IN_BASE) {
                if (hitChance(toSquare, to, after, gone) == 0.0) gain += tuning.leaveBaseBonus
                // Another piece out = more choices on later rolls, worth most early on.
                gain += tuning.flexBonus * (1 - mine.sumOf { it.coerceAtLeast(0) } / (PIECES_PER_COLOUR * PROGRESS_FINISH.toDouble()))
                why += "out "
            }
            if (toSquare != null) gain += tuning.chaseBonus.toDouble() * targetsAhead(toSquare, to, gone)

            val before = hitChance(squareOrNull(colour, from), from, mine, emptySet()) * worth(from)
            val then = hitChance(toSquare, to, after, gone) * worth(to)
            var partner = 0.0
            if (pairs && from in 0..PROGRESS_ENTER_HOME) {
                // Leaving a protected pair exposes the piece left behind.
                val fromSquare = squareOf(colour, from)
                val left = mine.withIndex().filter { (i, p) -> i != option && p in 0..PROGRESS_ENTER_HOME && squareOf(colour, p) == fromSquare }
                if (left.size == 1 && !RoyalLudoBoardUtils.isSafeSquare(fromSquare)) {
                    val p = left.single().value
                    partner = hitChance(fromSquare, p, after, gone) * worth(p)
                }
            }
            return Evaluation(gain = gain, risk = then - before + partner, note = why.trim())
        }

        /** Equal value: the threatened piece furthest behind first, then the lowest number. */
        override val tieBreak: Comparator<Int> = compareByDescending<Int> {
            hitChance(squareOrNull(colour, mine[it]), mine[it], mine, emptySet()) > 0
        }.thenBy { mine[it] }.thenBy { it }

        /** A seat's race score: total progress of its pieces (finished = 56). */
        private fun total(other: Int): Double = s.piecesForSeat(other).sumOf { it.coerceAtLeast(0) }.toDouble()

        /** A piece's value in play: its progress plus what getting out of base costs. */
        private fun worth(progress: Int): Double = progress.coerceAtLeast(0) + tuning.baseCost.toDouble()

        /**
         * Chance an opponent hits a piece of ours at [progress] on [square] before our next turn
         * (0 when safe, in the home lane, or a protected pair). [ours] is our pieces' progress
         * after the move; [gone] are opponent pieces this move captures. Threats: a piece 1–6
         * behind (1 in 6), 7–12 behind (a six then the rest, 1 in 36, with the extra-turn-on-six
         * rule), and a piece in base 1–6 past its start (a six out, then that number).
         */
        private fun hitChance(square: Int?, progress: Int, ours: List<Int>, gone: Set<Pair<Int, Int>>): Double {
            if (square == null || progress !in 0..PROGRESS_ENTER_HOME || RoyalLudoBoardUtils.isSafeSquare(square)) return 0.0
            if (pairs && ours.count { it in 0..PROGRESS_ENTER_HOME && squareOf(colour, it) == square } >= 2) return 0.0
            var miss = 1.0
            opponents().filter { (it.colour to it.progress) !in gone }.forEach { o ->
                val d = (square - squareOf(o.colour, o.progress) + TRACK_SIZE) % TRACK_SIZE
                if (o.progress + d > PROGRESS_ENTER_HOME) return@forEach
                val chance = when (d) {
                    in 1..DICE_FACES -> ONE_ROLL
                    in DICE_FACES + 1..2 * DICE_FACES -> if (house.extraTurnOnSix) SIX_THEN_REST else 0.0
                    else -> 0.0
                }
                miss *= 1 - chance
            }
            if (house.extraTurnOnSix) {
                waitingInBase().forEach { c ->
                    val d = (square - RoyalLudoBoardUtils.startSquareFor(c) + TRACK_SIZE) % TRACK_SIZE
                    if (d in 1..DICE_FACES) miss *= 1 - SIX_THEN_REST
                }
            }
            return (1 - miss).coerceAtMost(MAX_HIT)
        }

        /** Opponent pieces on [square] this move would send home (a protected pair can't be hit). */
        private fun capturable(square: Int): List<Opponent> =
            opponents().filter { squareOf(it.colour, it.progress) == square }
                .groupBy { it.colour }.values.filter { !pairs || it.size == 1 }.flatten()

        /** Opponent pieces 1–6 squares ahead of [square] that we could hit next turn. */
        private fun targetsAhead(square: Int, progress: Int, gone: Set<Pair<Int, Int>>): Int {
            val there = opponents().filter { (it.colour to it.progress) !in gone }
            return there.count { o ->
                val sq = squareOf(o.colour, o.progress)
                val d = (sq - square + TRACK_SIZE) % TRACK_SIZE
                val pairOnIt = pairs && there.count { it.colour == o.colour && squareOf(it.colour, it.progress) == sq } >= 2
                d in 1..DICE_FACES && progress + d <= PROGRESS_ENTER_HOME && !RoyalLudoBoardUtils.isSafeSquare(sq) && !pairOnIt
            }
        }

        /** Every opponent piece on the shared track; [Opponent.leader] marks the rival furthest ahead in the race. */
        private fun opponents(): List<Opponent> {
            val rivals = s.seats.map { it.value }.filter { s.colourForSeat(it) != colour && it !in s.removalOrder }
            val leader = rivals.maxByOrNull { total(it) }?.takeIf { total(it) >= total(seat) }
            return rivals.flatMap { other ->
                val c = s.colourForSeat(other)
                s.piecesForSeat(other).filter { it in 0..PROGRESS_ENTER_HOME }.map { Opponent(c, it, other == leader) }
            }
        }

        /** Colours with a piece still in base (they can come out with a six). */
        private fun waitingInBase(): List<Int> =
            s.seats.map { it.value }
                .filter { s.colourForSeat(it) != colour && it !in s.removalOrder && PROGRESS_IN_BASE in s.piecesForSeat(it) }
                .map { s.colourForSeat(it) }
    }

    private data class Opponent(val colour: Int, val progress: Int, val leader: Boolean)

    private fun squareOf(colour: Int, progress: Int) =
        RoyalLudoBoardUtils.absoluteSquare(RoyalLudoBoardUtils.startSquareFor(colour), progress)

    private fun squareOrNull(colour: Int, progress: Int): Int? = if (progress in 0..PROGRESS_ENTER_HOME) squareOf(colour, progress) else null

    private companion object {
        const val PERCENT = 100.0
        const val ONE_ROLL = 1.0 / 6
        const val SIX_THEN_REST = 1.0 / 36
        const val MAX_HIT = 0.95
    }
}

/**
 * Royal Ludo bot values (runtime settings ludo.bot_* and bot.standing_*):
 * a piece in play is worth its progress + [baseCost]; a capture is worth the
 * victim × [captureWeightPct] % (× [endgameCapturePct] % against the rival
 * leading the race), + [extraTurnBonus] for an extra turn the rules give;
 * finishing [finishBonus]; leaving base safely [leaveBaseBonus], plus
 * [flexBonus] early on; each opponent piece 1–6 squares ahead that it could
 * hit adds [chaseBonus]. Risk counts × [riskWeightPct] % × the standing
 * factor ([standingMinPct] % far behind → [standingMaxPct] % leading).
 */
data class RoyalLudoBotTuning(
    val baseCost: Int = 30,
    val captureWeightPct: Int = 200,
    val endgameCapturePct: Int = 150,
    val extraTurnBonus: Int = 10,
    val finishBonus: Int = 200,
    val leaveBaseBonus: Int = 40,
    val flexBonus: Int = 20,
    val riskWeightPct: Int = 300,
    val chaseBonus: Int = 5,
    val standingMinPct: Int = 60,
    val standingMaxPct: Int = 140,
)
