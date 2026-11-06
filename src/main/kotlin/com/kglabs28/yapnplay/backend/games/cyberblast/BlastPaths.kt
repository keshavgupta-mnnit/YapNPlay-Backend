package com.kglabs28.yapnplay.backend.games.cyberblast

/** Every tile reached from [start]: its distance in steps and the tile it was reached from. */
class BlastPathTree(val start: Int, val steps: Map<Int, Int>, private val parent: Map<Int, Int>) {
    val tiles: Set<Int> get() = steps.keys

    /** The tiles from [start] (not included) to [tile], in order; empty when it is [start] or out of reach. */
    fun path(tile: Int): List<Int> {
        if (tile !in steps || tile == start) return emptyList()
        val out = ArrayDeque<Int>()
        var at = tile
        while (at != start) {
            out.addFirst(at)
            at = parent.getValue(at)
        }
        return out
    }

    /** Which way to walk first to get to [tile]. */
    fun firstDir(tile: Int): BlastDir? {
        val first = path(tile).firstOrNull() ?: return null
        return BlastGrid.dirBetween(start, first)
    }
}

/** Shortest walks on the board (breadth first). */
object BlastPaths {
    /** Tiles reachable from [start] in at most [maxSteps], walking only where [canStep] (the step's tile and how many steps it took). */
    fun from(state: CyberBlastState, start: Int, maxSteps: Int, canStep: (tile: Int, steps: Int) -> Boolean): BlastPathTree {
        val steps = mutableMapOf(start to 0)
        val parent = mutableMapOf<Int, Int>()
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            val next = steps.getValue(at) + 1
            if (next > maxSteps) continue
            for (n in BlastGrid.neighbours(state, at)) {
                if (n in steps || !canStep(n, next)) continue
                steps[n] = next
                parent[n] = at
                queue.addLast(n)
            }
        }
        return BlastPathTree(start, steps, parent)
    }
}
