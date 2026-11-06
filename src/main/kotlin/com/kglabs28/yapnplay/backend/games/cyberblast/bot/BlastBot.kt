package com.kglabs28.yapnplay.backend.games.cyberblast.bot

import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_HALF_TILE
import com.kglabs28.yapnplay.backend.games.cyberblast.BLAST_TILE
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastDir
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastDropBomb
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastGrid
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPhase
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastPlayer
import com.kglabs28.yapnplay.backend.games.cyberblast.BlastSteer
import com.kglabs28.yapnplay.backend.games.cyberblast.CyberBlastState

/**
 * Plays Cyber Blast seats (CPUs, and players who are away or left), every
 * frame. Like a person it looks at the board only every so often (a
 * reaction time), then picks where to go or bombs ([BlastGoalDecision]);
 * between looks it walks the path it chose. It never bombs twice in quick
 * succession. Its memory lives only as long as the match runs here.
 */
class BlastBot(
    private val settings: () -> BlastBotSettings,
    private val random: GameRandom,
    val trace: BotTrace = BotTrace(),
) {
    private val minds = mutableMapOf<Int, BlastMind>()

    /** What [seats] do this frame (steering changes and bombs), in order. */
    fun act(state: CyberBlastState, seats: Set<Int>): List<Pair<Int, Action>> {
        if (state.phase != BlastPhase.PLAYING) return emptyList()
        val tuning = settings()
        val out = mutableListOf<Pair<Int, Action>>()
        seats.sorted().forEach { seat ->
            val me = state.player(seat)?.takeIf { it.inPlay } ?: return@forEach
            var mind = minds[seat] ?: BlastMind(nextBombAt = state.time + jitter(tuning.bombGapMs, tuning.bombGapJitterMs))
            if (state.time >= mind.nextLookAt) {
                val ready = state.time >= mind.nextBombAt && (state.mandatory.bombs == 0 || state.bombsOf(seat) < state.mandatory.bombs)
                val decision = BlastGoalDecision(state, me, tuning, ready)
                val goal = decision.choose(trace)
                mind = when {
                    goal == null -> mind.copy(path = emptyList())
                    goal.bomb -> {
                        out += seat to BlastDropBomb
                        // Looks again at once, with its bomb on the board: time to run.
                        mind.copy(path = emptyList(), nextBombAt = state.time + jitter(tuning.bombGapMs, tuning.bombGapJitterMs), nextLookAt = state.time)
                    }
                    else -> mind.copy(path = decision.walk.path(goal.tile))
                }
                if (goal?.bomb != true) mind = mind.copy(nextLookAt = state.time + jitter(tuning.reactMs, tuning.reactJitterMs))
            }
            val (dir, path) = follow(me, mind.path)
            mind = mind.copy(path = path)
            minds[seat] = mind
            if (dir != me.held) out += seat to BlastSteer(dir)
        }
        return out
    }

    /** Forgets a seat (it left the board, or came back to its owner). */
    fun forget(seat: Int) {
        minds.remove(seat)
    }

    /**
     * The way to push along [path]: towards its next tile; on the last tile,
     * on until its centre, then stop.
     */
    private fun follow(me: BlastPlayer, path: List<Int>): Pair<BlastDir?, List<Int>> {
        var rest = path
        while (rest.isNotEmpty() && rest.first() == me.tile && (rest.size > 1 || pastCentre(me))) rest = rest.drop(1)
        val next = rest.firstOrNull() ?: return null to emptyList()
        if (next == me.tile) return me.held to rest
        val dir = BlastGrid.dirBetween(me.tile, next) ?: return null to emptyList()
        return dir to rest
    }

    /** At or past the middle of its tile, the way it walks. */
    private fun pastCentre(me: BlastPlayer): Boolean {
        val dir = me.held ?: return true
        val pos = if (dir.horizontal) me.x else me.y
        val centre = (pos / BLAST_TILE) * BLAST_TILE + BLAST_HALF_TILE
        return (centre - pos) * (if (dir.horizontal) dir.dx else dir.dy) <= 0
    }

    private fun jitter(base: Long, spread: Long): Long = base + if (spread > 0) random.nextInt(spread.toInt() + 1) else 0
}
