package com.kglabs28.yapnplay.backend.games.cyberblast

import kotlin.math.abs

/** What a blast from one tile reaches: the fire tiles (with their shape), the blockers it breaks and the bombs it sets off. */
data class BlastReach(val fire: List<Pair<Int, FlameShape>>, val crates: Set<Int>, val bombs: Set<Int>)

/**
 * The board's geometry: tiles, pillars, what blocks a walk or a blast.
 * A tile is `row * STRIDE + col` whatever the board's size (a Big Arena
 * is bigger), so a tile number never depends on the board; only its
 * edges do ([inside], [next], [tiles] take the state).
 */
object BlastGrid {
    /** Tiles per row in a tile number: more than any board's width. */
    const val STRIDE = 64

    fun tileAt(x: Int, y: Int): Int = (y / BLAST_TILE) * STRIDE + x / BLAST_TILE
    fun col(tile: Int): Int = tile % STRIDE
    fun row(tile: Int): Int = tile / STRIDE
    fun of(col: Int, row: Int): Int = row * STRIDE + col
    fun centreX(tile: Int): Int = col(tile) * BLAST_TILE + BLAST_HALF_TILE
    fun centreY(tile: Int): Int = row(tile) * BLAST_TILE + BLAST_HALF_TILE

    /** Metal pillars stand on every odd column of every odd row; they never break. */
    fun isPillar(tile: Int): Boolean = col(tile) % 2 == 1 && row(tile) % 2 == 1

    fun inside(state: CyberBlastState, col: Int, row: Int): Boolean = col in 0 until state.cols && row in 0 until state.rows

    /** Every tile of the board, row by row. */
    fun tiles(state: CyberBlastState): List<Int> = tiles(state.cols, state.rows)

    fun tiles(cols: Int, rows: Int): List<Int> = (0 until rows).flatMap { r -> (0 until cols).map { c -> of(c, r) } }

    /** The tile next to [tile] towards [dir], or null past the board's edge. */
    fun next(state: CyberBlastState, tile: Int, dir: BlastDir): Int? {
        val c = col(tile) + dir.dx
        val r = row(tile) + dir.dy
        return if (inside(state, c, r)) of(c, r) else null
    }

    fun neighbours(state: CyberBlastState, tile: Int): List<Int> = BlastDir.entries.mapNotNull { next(state, tile, it) }

    /** The way from [from] to the tile right beside it, [to]; null when they aren't side by side. */
    fun dirBetween(from: Int, to: Int): BlastDir? =
        BlastDir.entries.firstOrNull { col(from) + it.dx == col(to) && row(from) + it.dy == row(to) }

    fun distance(a: Int, b: Int): Int = abs(col(a) - col(b)) + abs(row(a) - row(b))

    /** A pillar or a closed-in wall: nothing passes, nothing breaks it. */
    fun isWall(state: CyberBlastState, tile: Int): Boolean = isPillar(tile) || tile in state.walls

    /** Blocks walking and stops a blast: a wall or a blocker. */
    fun isSolid(state: CyberBlastState, tile: Int): Boolean = isWall(state, tile) || tile in state.crates

    /** A player may walk onto [tile]: nothing solid, and no bomb unless they're still on it (just dropped it). */
    fun canEnter(state: CyberBlastState, tile: Int, from: Int): Boolean =
        !isSolid(state, tile) && (tile == from || state.bombAt(tile) == null)

    /** Free floor: no wall, blocker or bomb. */
    fun isOpen(state: CyberBlastState, tile: Int): Boolean = !isSolid(state, tile) && state.bombAt(tile) == null

    /**
     * Fire from [tile] [radius] tiles each way: walls stop it before, a
     * blocker or a bomb stops it on that tile (the blocker breaks, the bomb
     * goes off).
     */
    fun reach(state: CyberBlastState, tile: Int, radius: Int): BlastReach {
        val fire = mutableListOf(tile to FlameShape.CENTRE)
        val crates = mutableSetOf<Int>()
        val bombs = mutableSetOf<Int>()
        for (dir in BlastDir.entries) {
            var at = tile
            for (step in 1..radius) {
                at = next(state, at, dir) ?: break
                if (isWall(state, at)) break
                fire += at to if (dir.horizontal) FlameShape.HORIZONTAL else FlameShape.VERTICAL
                if (at in state.crates) {
                    crates += at
                    break
                }
                if (state.bombAt(at) != null) {
                    bombs += at
                    break
                }
            }
        }
        return BlastReach(fire, crates, bombs)
    }
}
