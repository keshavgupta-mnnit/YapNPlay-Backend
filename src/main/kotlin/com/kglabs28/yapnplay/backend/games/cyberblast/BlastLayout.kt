package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Where the blockers start. One quarter of the arena is filled at random
 * ([densityPct] of its free tiles) and mirrored left to right and top to
 * bottom, so the arena looks laid out by hand (every side alike, fair for
 * whoever starts where) rather than scattered.
 */
object BlastLayout {
    fun crates(cols: Int, rows: Int, densityPct: Int, random: GameRandom): Set<Int> {
        val out = mutableSetOf<Int>()
        // The quarter, the middle column and row included (they mirror onto themselves).
        for (r in 0..(rows - 1) / 2) for (c in 0..(cols - 1) / 2) {
            val tile = BlastGrid.of(c, r)
            if (BlastGrid.isPillar(tile) || random.nextInt(PERCENT) >= densityPct) continue
            listOf(c to r, cols - 1 - c to r, c to rows - 1 - r, cols - 1 - c to rows - 1 - r).forEach { (mc, mr) -> out += BlastGrid.of(mc, mr) }
        }
        return out
    }

    private const val PERCENT = 100
}
