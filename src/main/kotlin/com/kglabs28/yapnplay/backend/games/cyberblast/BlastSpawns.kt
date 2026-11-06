package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Where players appear: spread out at the start, and after a hit on a
 * random tile with no fire coming, away from the others, with room to walk
 * and a way out of their own bomb's reach. A spot boxed in by blockers gets
 * a path opened, so nobody is ever trapped where they appear.
 */
object BlastSpawns {

    /** Spread-out starting tiles for [seatCount] players on an empty [cols] × [rows] board. */
    fun startTiles(cols: Int, rows: Int, seatCount: Int, random: GameRandom): List<Int> {
        val floor = BlastGrid.tiles(cols, rows).filterNot(BlastGrid::isPillar)
        val chosen = mutableListOf(floor[random.nextInt(floor.size)])
        while (chosen.size < seatCount) {
            val scored = floor.filter { it !in chosen }.map { tile -> tile to chosen.minOf { BlastGrid.distance(it, tile) } }
            val best = scored.maxOf { it.second }
            val far = scored.filter { it.second >= best - SPREAD_SLACK }
            chosen += far[random.nextInt(far.size)].first
        }
        return chosen
    }

    /** A safe tile for [seat] to come back on (see the class note); its crates are cleared if it had to be carved out. */
    fun respawn(state: CyberBlastState, seat: Int, random: GameRandom): Pair<CyberBlastState, Int> {
        val danger = BlastDanger.of(state)
        val radius = state.player(seat)?.radius ?: state.mandatory.startRadius
        val others = state.alive.filter { it.seat != seat }.map { it.tile }
        val open = BlastGrid.tiles(state).filter { BlastGrid.isOpen(state, it) && it !in danger && it !in others }
        val good = open.filter { hasEscape(state, it, radius, danger) }
        val pool = good.ifEmpty { open }
        if (pool.isEmpty()) {
            // Nothing open at all (the arena nearly closed): any floor tile, carved free.
            val floor = BlastGrid.tiles(state).filter { !BlastGrid.isWall(state, it) }
            val tile = floor[random.nextInt(floor.size)]
            return openEscape(state.copy(crates = state.crates - tile), tile, radius) to tile
        }
        val tile = farFrom(pool, others, random)
        return (if (tile in good) state else openEscape(state, tile, radius)) to tile
    }

    /**
     * [tile] has room: at least a few free tiles within reach, one of them out of a
     * [radius] blast from [tile], all without fire coming.
     */
    fun hasEscape(state: CyberBlastState, tile: Int, radius: Int, danger: Map<Int, DangerWindow>): Boolean {
        val walk = BlastPaths.from(state, tile, BLAST_ESCAPE_DEPTH) { t, _ -> BlastGrid.isOpen(state, t) && t !in danger }
        if (walk.tiles.size < BLAST_MIN_ROOM) return false
        val fire = BlastGrid.reach(state, tile, radius).fire.map { it.first }.toSet()
        return walk.tiles.any { it !in fire }
    }

    /**
     * Breaks the blockers on the shortest way from [tile] to a tile out of its
     * [radius] blast (walking through blockers, never walls), so a player there
     * can always get away from their own first bomb.
     */
    fun openEscape(state: CyberBlastState, tile: Int, radius: Int): CyberBlastState {
        val fire = BlastGrid.reach(state.copy(crates = emptySet()), tile, radius).fire.map { it.first }.toSet()
        val walk = BlastPaths.from(state, tile, Int.MAX_VALUE) { t, _ -> !BlastGrid.isWall(state, t) && state.bombAt(t) == null }
        val exit = walk.tiles.filter { it !in fire }.minByOrNull { walk.steps.getValue(it) } ?: return state
        // Its neighbours too, so there's room to stand, not just a corridor.
        val cleared = walk.path(exit).toSet() + tile + BlastGrid.neighbours(state, exit).filter { !BlastGrid.isWall(state, it) }.take(1)
        return state.copy(crates = state.crates - cleared)
    }

    /** One of the tiles of [pool] farthest from [others] (at random among the farthest). */
    private fun farFrom(pool: List<Int>, others: List<Int>, random: GameRandom): Int {
        if (others.isEmpty()) return pool[random.nextInt(pool.size)]
        val scored = pool.map { tile -> tile to others.minOf { BlastGrid.distance(it, tile) } }
        val best = scored.maxOf { it.second }
        val far = scored.filter { it.second >= best - SPREAD_SLACK }
        return far[random.nextInt(far.size)].first
    }

    /** Picks among tiles at most this many steps less far than the farthest. */
    private const val SPREAD_SLACK = 2
}
