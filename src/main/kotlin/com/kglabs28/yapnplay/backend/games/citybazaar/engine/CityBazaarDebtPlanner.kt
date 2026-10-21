package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard

/**
 * Picks what a seat in debt sells next, as a person would: by what each sale
 * really costs. Each owned city offers one step: its top building (back to
 * the bank) or, with none left, the bare city (to auction). A step's loss is
 * the rent the seat should no longer earn ([CityBazaarOutlook.future] before
 * − after, which sees a broken colour set or company pair and how well the
 * city has earned), the money lost selling below cost (build cost − resale,
 * or price − bank value), and, for a city, what a rival gains if it
 * completes or strengthens their set ([denial]). Two plans are compared: the
 * one covering step with the least loss, and a greedy run of least loss per
 * rupee until the debt is covered (re-evaluated after each step). The plan
 * with the smaller total loss wins and its first step is played; it is
 * planned again after every sale.
 */
object CityBazaarDebtPlanner {
    private const val PERCENT = 100.0

    /** (tile, true) to sell its top building, (tile, false) for the bare city; null when nothing is owned. */
    fun next(s: CityBazaarState, seat: Int, tuning: CityBazaarBotTuning = CityBazaarBotTuning()): Pair<Int, Boolean>? {
        val need = ((s.debt?.total ?: 0) - s.cashOf(seat)).coerceAtLeast(0)
        val first = steps(s, seat, tuning)
        if (first.isEmpty()) return null
        val single = first.filter { it.raise >= need }.minWithOrNull(compareBy<Step> { it.loss }.thenBy { it.raise })
        val greedy = greedy(s, seat, need, tuning)
        val best = when {
            single == null -> greedy.firstOrNull()
            greedy.isEmpty() || greedy.sumOf { it.loss } >= single.loss -> single
            else -> greedy.first()
        }
        return best?.let { it.tile to it.building }
    }

    /**
     * What keeping [tile] from a rival is worth to [seat]: the most rent a
     * rival would gain from it, counting only rivals it completes or
     * strengthens a set for, × the denial weight.
     */
    fun denial(s: CityBazaarState, seat: Int, tile: Int, tuning: CityBazaarBotTuning): Double =
        (
            s.active.filter { it != seat && CityBazaarBot.strengthens(s, it, tile) }
                .maxOfOrNull { rival ->
                    CityBazaarOutlook.future(s.copy(owners = s.owners + (tile to rival)), rival, tuning.defaultRounds) -
                        CityBazaarOutlook.future(s, rival, tuning.defaultRounds)
                } ?: 0.0
            ) * tuning.denialWeightPct / PERCENT

    /** Least loss per rupee raised, one step at a time, until [need] is covered or nothing is left. */
    private fun greedy(start: CityBazaarState, seat: Int, need: Int, tuning: CityBazaarBotTuning): List<Step> {
        val plan = mutableListOf<Step>()
        var s = start
        var raised = 0
        while (raised < need) {
            val step = steps(s, seat, tuning)
                .minWithOrNull(compareBy<Step> { it.loss / it.raise.coerceAtLeast(1) }.thenByDescending { it.raise })
                ?: break
            plan += step
            raised += step.raise
            s = step.after
        }
        return plan
    }

    private fun steps(s: CityBazaarState, seat: Int, tuning: CityBazaarBotTuning): List<Step> {
        val before = CityBazaarOutlook.future(s, seat, tuning.defaultRounds)
        return s.owners.filter { it.value == seat }.keys.sorted().map { tile ->
            val spec = CityBazaarBoard.tile(tile)
            val building = s.level(tile) > 0
            val after = if (building) s.copy(levels = s.levels + (tile to s.level(tile) - 1)) else s.copy(owners = s.owners - tile)
            val raise = if (building) CityBazaarEconomy.buildingResale(tile) else CityBazaarEconomy.tileResale(tile)
            val capitalLost = if (building) spec.buildCost - raise else spec.price - raise
            val rivalBoost = if (building) 0.0 else denial(after, seat, tile, tuning)
            Step(tile, building, raise, before - CityBazaarOutlook.future(after, seat, tuning.defaultRounds) + capitalLost + rivalBoost, after)
        }
    }

    private class Step(val tile: Int, val building: Boolean, val raise: Int, val loss: Double, val after: CityBazaarState)
}
