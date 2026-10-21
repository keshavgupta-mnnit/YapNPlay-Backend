package com.kglabs28.yapnplay.backend.games.base.play

/**
 * How every game's bot decides (City Bazaar, Royal Ludo, and any game
 * added later; docs/ARCHITECTURE_RULES.md "Game bots decide like a
 * person"): list the options the rules allow, give each an [Evaluation]
 * (expected gain, cost now, and risk = chance of a loss × its size), and
 * take the one with the highest gain − cost − risk × [riskWeight].
 * Reserves and cushions belong in the risk, never in a stand-alone rule.
 */
interface BotDecision<T> {
    /** What this decision is called in the bot's trace (e.g. "buy Mumbai"). */
    val name: String

    /** Everything the bot could do here. */
    fun options(): List<T>

    fun evaluate(option: T): Evaluation

    /** How much risk counts: the standing factor (behind → bolder, leading → careful). */
    val riskWeight: Double get() = 1.0

    /** Breaks exact ties (first wins); by default the first listed option. */
    val tieBreak: Comparator<T>? get() = null

    /** A short label for an option in the trace. */
    fun label(option: T): String = option.toString()

    /** The option worth the most (recorded in [trace] with the runners-up), or null when there is none. */
    fun choose(trace: BotTrace? = null): T? {
        val scored = options().map { it to evaluate(it) }
        val values = scored.map { (option, e) -> option to e.value(riskWeight) }
        val best = values.maxOfOrNull { it.second } ?: return null
        val top = values.filter { it.second >= best - EPSILON }.map { it.first }
        val chosen = tieBreak?.let { top.sortedWith(it).first() } ?: top.first()
        trace?.record(name, label(chosen), scored.sortedByDescending { it.second.value(riskWeight) }.take(TRACE_OPTIONS).map { (o, e) -> label(o) to e }, riskWeight)
        return chosen
    }

    private companion object {
        const val EPSILON = 1e-9
        const val TRACE_OPTIONS = 3
    }
}

/**
 * One option's expected [gain] (future earnings, progress…), [cost] paid
 * now, and [risk] (chance of a loss × its size). [note] says why, for the
 * trace. Use [Evaluation.never] for an option a person would never take.
 */
data class Evaluation(
    val gain: Double = 0.0,
    val cost: Double = 0.0,
    val risk: Double = 0.0,
    val note: String = "",
) {
    fun value(riskWeight: Double): Double = gain - cost - risk * riskWeight

    companion object {
        fun never(note: String) = Evaluation(cost = Double.POSITIVE_INFINITY, note = note)
        fun always(note: String) = Evaluation(gain = Double.POSITIVE_INFINITY, note = note)
    }
}

/**
 * Behind → take more risk, leading → less: [min] + ([max] − [min]) × how
 * close the seat is to the leader (its score / the best score, 0–1).
 */
object Standing {
    fun factor(myScore: Double, bestScore: Double, min: Double, max: Double): Double {
        val share = if (bestScore <= 0) 1.0 else (myScore / bestScore).coerceIn(0.0, 1.0)
        return min + (max - min) * share
    }
}
