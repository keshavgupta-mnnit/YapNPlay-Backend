package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * The match clock moving on, in small equal steps: the countdown, then
 * walking, bombs going off, fire burning out, hits, power-ups and the
 * closing arena, and the end once fewer than two players have hearts left.
 */
object BlastSimulation {

    fun advance(state: CyberBlastState, elapsedMs: Long, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        var s = state
        val events = mutableListOf<BlastEvent>()
        var left = elapsedMs
        while (left > 0 && s.phase != BlastPhase.FINISHED) {
            val dt = minOf(left, BLAST_MAX_STEP_MS)
            left -= dt
            val (next, stepEvents) = step(s, dt, random)
            s = next
            events += stepEvents
        }
        return s to events
    }

    private fun step(state: CyberBlastState, dt: Long, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        val rules = state.mandatory
        var s = state.copy(time = state.time + dt)
        if (s.phase == BlastPhase.COUNTDOWN) {
            if (s.time < rules.countdownMs) return s to emptyList()
            return s.copy(phase = BlastPhase.PLAYING, nextItemAt = s.time + rules.itemEveryMs) to listOf(BlastStarted)
        }
        val events = mutableListOf<BlastEvent>()
        s = s.copy(players = s.players.map { p ->
            if (!p.inPlay || p.held == null) p else BlastMovement.walk(s, p, distance(p, s.time, dt, rules))
        })
        s = BlastExplosions.burnOut(s)
        val (exploded, boom) = BlastExplosions.detonate(s)
        s = exploded
        boom?.let(events::add)
        BlastHits.burn(s, random).let { (next, hits) -> s = next; events += hits }
        BlastPowerUps.update(s, random).let { (next, items) -> s = next; events += items }
        BlastClosing.update(s, random).let { (next, crushed) -> s = next; events += crushed }
        val ended = endIfDecided(s)
        if (ended !== s) events += BlastFinished
        return ended to events
    }

    /** Fewer than two players with hearts left: the match is over. */
    fun endIfDecided(state: CyberBlastState): CyberBlastState =
        if (state.phase != BlastPhase.FINISHED && state.alive.size < BLAST_MIN_ALIVE) state.copy(phase = BlastPhase.FINISHED) else state

    /** How far [p] walks in [dt] ms: the boots' speed while they last. */
    private fun distance(p: BlastPlayer, now: Long, dt: Long, rules: CyberBlastMandatoryRules): Int =
        ((if (p.boosted(now)) rules.boostSpeed else rules.speed) * dt / MILLIS_PER_SECOND).toInt()

    private const val MILLIS_PER_SECOND = 1_000L
}
