package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Late in a match the arena closes in, so it always ends: one tile at a
 * time, round the outside and spiralling in, becomes a wall until only a
 * small core is left. Whatever is on that tile goes; a player standing there
 * loses a heart (no points) and comes back inside.
 */
object BlastClosing {

    private val orders = mutableMapOf<Pair<Int, Int>, List<Int>>()

    /** Tiles in the order they close on a [cols] × [rows] board: the outer ring clockwise from the top left, then the next ring in. */
    fun order(cols: Int, rows: Int): List<Int> = orders.getOrPut(cols to rows) { spiral(cols, rows) }

    private fun spiral(cols: Int, rows: Int): List<Int> = buildList {
        var left = 0
        var top = 0
        var right = cols - 1
        var bottom = rows - 1
        val coreCol = cols / 2
        val coreRow = rows / 2
        val inCore = { tile: Int ->
            kotlin.math.abs(BlastGrid.col(tile) - coreCol) <= BLAST_CORE_HALF_COLS && kotlin.math.abs(BlastGrid.row(tile) - coreRow) <= BLAST_CORE_HALF_ROWS
        }
        while (left <= right && top <= bottom) {
            val ring = buildList {
                for (c in left..right) add(BlastGrid.of(c, top))
                for (r in top + 1..bottom) add(BlastGrid.of(right, r))
                if (bottom > top) for (c in right - 1 downTo left) add(BlastGrid.of(c, bottom))
                if (right > left) for (r in bottom - 1 downTo top + 1) add(BlastGrid.of(left, r))
            }
            ring.filter { !BlastGrid.isPillar(it) && !inCore(it) && it !in this }.forEach(::add)
            left++
            top++
            right--
            bottom--
        }
    }

    /** Closing in: it has started, its time is up, or few players are left and most blockers are gone (nothing left to fight over). */
    private fun due(state: CyberBlastState): Boolean {
        val rules = state.mandatory
        if (state.nextCloseAt != 0L) return true
        val timeUp = rules.closeAfterMs > 0 && state.time >= rules.closeAfterMs
        val cleared = rules.closeWhenCratesLeftPct > 0 && state.startCrates > 0 && state.crates.size * PERCENT <= state.startCrates * rules.closeWhenCratesLeftPct
        val fewLeft = state.alive.size <= rules.closeWhenPlayersLeft
        return timeUp || (cleared && fewLeft)
    }

    fun update(state: CyberBlastState, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        val rules = state.mandatory
        if (!due(state) || state.closed >= order(state.cols, state.rows).size) return state to emptyList()
        if (state.nextCloseAt == 0L) return state.copy(nextCloseAt = state.time) to emptyList()
        if (state.time < state.nextCloseAt) return state to emptyList()
        val tile = order(state.cols, state.rows)[state.closed]
        var s = state.copy(
            closed = state.closed + 1,
            nextCloseAt = state.time + rules.closeStepMs,
            walls = state.walls + tile,
            crates = state.crates - tile,
            bombs = state.bombs.filter { it.tile != tile },
            flames = state.flames.filter { it.tile != tile },
            items = state.items.filter { it.tile != tile },
        )
        val events = mutableListOf<BlastEvent>()
        s.alive.filter { it.tile == tile }.forEach { p ->
            val (next, hit) = BlastHits.hit(s, p.seat, p.seat, crushed = true, random)
            s = next
            events += hit
        }
        return s to events
    }

    /** How long until the arena starts closing (null: it never does, or it has started). */
    fun startsIn(state: CyberBlastState): Long? {
        val after = state.mandatory.closeAfterMs
        return if (state.nextCloseAt != 0L || after <= 0 || state.time >= after) null else after - state.time
    }

    private const val PERCENT = 100
}
