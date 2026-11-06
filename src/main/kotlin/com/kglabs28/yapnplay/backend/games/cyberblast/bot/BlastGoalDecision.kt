package com.kglabs28.yapnplay.backend.games.cyberblast.bot

import com.kglabs28.yapnplay.backend.games.base.play.BotDecision
import com.kglabs28.yapnplay.backend.games.base.play.Evaluation
import com.kglabs28.yapnplay.backend.games.base.play.Standing
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastBomb
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastDanger
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastGrid
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPathTree
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPaths
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPlayer
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastState
import com.kglabs28.yapnplay.backend.games.cyberblast.DangerWindow

/**
 * Where a Cyber Blast bot goes next, or whether it bombs where it stands,
 * the way a person weighs it (ARCHITECTURE_RULES §5.9). Options: every tile
 * it can walk to without stepping into fire on the way, and a bomb here.
 * Each is worth what it may earn (blockers broken, players caught — likelier
 * when they're boxed in —, speed boots, getting closer to someone), minus
 * the walk, minus the risk of losing a heart there (fire coming, players
 * who have the tile in their line). A bomb with no way out in time is never
 * dropped. A bot far behind takes more risks, a leader fewer.
 */
class BlastGoalDecision(
    private val state: CyberBlastState,
    private val me: BlastPlayer,
    private val settings: BlastBotSettings,
    /** It may drop a bomb now (one left, its pause between bombs over). */
    private val bombReady: Boolean,
) : BotDecision<BlastGoal> {
    override val name: String = "seat ${me.seat}"

    private val rules = state.mandatory
    private val now = state.time
    private val stepMs = MICROS_PER_TILE / (if (me.boosted(now)) rules.boostSpeed else rules.speed)
    private val danger = BlastDanger.of(state)
    private val others = state.alive.filter { it.seat != me.seat }

    /** Where it can walk safely: never onto a tile while fire is (or will be) there. */
    val walk: BlastPathTree = BlastPaths.from(state, me.tile, MAX_STEPS) { tile, steps -> BlastGrid.isOpen(state, tile) && clear(danger[tile], steps) }

    /** How many players have each tile in their bomb's line right now. */
    private val inLine: Map<Int, Int> = others.flatMap { o -> BlastGrid.reach(state, o.tile, o.radius).fire.map { it.first } }.groupingBy { it }.eachCount()

    override val riskWeight: Double = run {
        val low = state.alive.minOf { it.score }
        val best = state.alive.maxOf { it.score } - low
        Standing.factor((me.score - low).toDouble(), best.toDouble(), settings.standingMinPct / PERCENT, settings.standingMaxPct / PERCENT)
    }

    override fun options(): List<BlastGoal> =
        walk.tiles.map { BlastGoal(it, walk.steps.getValue(it), bomb = false) } +
            listOfNotNull(BlastGoal(me.tile, 0, bomb = true).takeIf { bombReady && BlastGrid.isOpen(state, me.tile) })

    override fun evaluate(option: BlastGoal): Evaluation = if (option.bomb) bombHere() else walkTo(option)

    override val tieBreak: Comparator<BlastGoal> = compareBy<BlastGoal> { it.steps }.thenBy { it.tile }

    private fun walkTo(goal: BlastGoal): Evaluation {
        val arrival = now + goal.steps * stepMs
        // Its own tile's bomb is the "bomb here" option, not a reason to wait.
        val planned = if (goal.tile == me.tile) 0.0 else bombValue(goal.tile, arrival)?.let { it * settings.plannedBombPct / PERCENT } ?: 0.0
        val boots = if (state.items.any { it.tile == goal.tile }) settings.boostValue * if (me.boosted(now)) USED_BOOTS else 1.0 else 0.0
        val nearest = others.minOfOrNull { BlastGrid.distance(it.tile, goal.tile) } ?: HUNT_RANGE
        val hunt = settings.huntValue * (HUNT_RANGE - nearest).coerceAtLeast(0)
        val fireRisk = danger[goal.tile]?.takeIf { it.until >= arrival }?.let { w ->
            // Fire coming where it would stand: it would have to run again, maybe too late.
            settings.lifeValue * if (w.from - arrival < stepMs * RUN_STEPS) 1.0 else LATER_FIRE
        } ?: 0.0
        val threat = settings.lifeValue * settings.threatPct / PERCENT * (inLine[goal.tile] ?: 0)
        return Evaluation(gain = planned + boots + hunt, cost = goal.steps * settings.stepCost, risk = fireRisk + threat, note = "bomb ${planned.toInt()}, boots ${boots.toInt()}, hunt ${hunt.toInt()}")
    }

    private fun bombHere(): Evaluation {
        val escape = escapeSteps(me.tile, now) ?: return Evaluation.never("no way out")
        val value = bombValue(me.tile, now) ?: return Evaluation.never("no way out")
        if (value <= 0.0) return Evaluation.never("nothing to hit")
        // Running out of its own blast: the tighter, the likelier a slip.
        val tight = (escape * stepMs).toDouble() / rules.fuseMs
        // A sure thing now beats the same chance later: a bomb dropped now counts a little more.
        return Evaluation(gain = value * NOW_BONUS, risk = settings.lifeValue * tight * tight * SLIP, note = "escape $escape steps")
    }

    /**
     * What a bomb dropped on [tile] at [at] would catch: blockers broken and players in
     * its fire (likelier the less room they have). Null when there'd be no way out in time.
     */
    private fun bombValue(tile: Int, at: Long): Double? {
        val reach = BlastGrid.reach(state, tile, me.radius)
        val fire = reach.fire.map { it.first }.toSet()
        val crates = reach.crates.size * rules.cratePoints.toDouble()
        val caught = others.filter { it.tile in fire && !it.shielded(at + rules.fuseMs) }.sumOf { rules.hitPoints * catchChance(it, fire) }
        val value = crates + caught
        if (value <= 0.0) return 0.0
        return if (escapeSteps(tile, at) == null) null else value
    }

    /** A player in the fire's way gets caught more often the fewer tiles they have to run to. */
    private fun catchChance(other: BlastPlayer, fire: Set<Int>): Double {
        val room = BlastPaths.from(state, other.tile, ROOM_STEPS) { t, _ -> BlastGrid.isOpen(state, t) }.tiles.count { it !in fire }
        val boxed = 1.0 - room.coerceAtMost(ROOM_FULL).toDouble() / ROOM_FULL
        return (settings.hitChancePct + settings.cornerBonusPct * boxed) / PERCENT
    }

    /** Steps from [tile] to a tile its own bomb (dropped at [at]) can't reach, in time; null: none. */
    private fun escapeSteps(tile: Int, at: Long): Int? {
        val bomb = BlastBomb(id = 0, tile = tile, owner = me.seat, radius = me.radius, explodesAt = at + rules.fuseMs)
        val withBomb = BlastDanger.of(state, bomb)
        val head = ((at - now) / stepMs).toInt()
        val run = BlastPaths.from(state, tile, MAX_STEPS) { t, steps -> BlastGrid.isOpen(state, t) && clear(withBomb[t], head + steps) }
        val out = run.tiles.filter { withBomb[it] == null }.minOfOrNull { run.steps.getValue(it) } ?: return null
        return out.takeIf { it * stepMs + MARGIN_MS < rules.fuseMs }
    }

    /** No fire on the tile while it walks through it at step [steps]. */
    private fun clear(window: DangerWindow?, steps: Int): Boolean {
        if (window == null) return true
        val at = now + steps * stepMs
        return !window.overlaps(at - MARGIN_MS, at + stepMs + MARGIN_MS)
    }

    private companion object {
        const val PERCENT = 100.0
        /** A tile's worth of walking at 1 tile a second, in ms × 1000 (speeds are thousandths of a tile a second). */
        const val MICROS_PER_TILE = 1_000_000L
        const val MAX_STEPS = 10
        const val MARGIN_MS = 200L
        const val HUNT_RANGE = 12
        const val ROOM_STEPS = 3
        const val ROOM_FULL = 6
        const val RUN_STEPS = 2
        const val LATER_FIRE = 0.5
        const val USED_BOOTS = 0.3
        const val SLIP = 0.1
        const val NOW_BONUS = 1.5
    }
}
