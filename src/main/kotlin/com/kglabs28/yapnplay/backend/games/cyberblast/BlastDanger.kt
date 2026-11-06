package com.kglabs28.yapnplay.backend.games.cyberblast

/** Fire will be on a tile from [from] until [until] (match time). */
data class DangerWindow(val from: Long, val until: Long) {
    fun overlaps(start: Long, end: Long): Boolean = start <= until && end >= from
}

/**
 * Where fire is now and where it will be: the flames burning plus every
 * bomb's blast, a bomb in another's reach going off with it (chains).
 * Respawns and the bots read it.
 */
object BlastDanger {

    fun of(state: CyberBlastState, extra: BlastBomb? = null): Map<Int, DangerWindow> {
        val windows = mutableMapOf<Int, DangerWindow>()
        fun mark(tile: Int, from: Long, until: Long) {
            val old = windows[tile]
            windows[tile] = if (old == null) DangerWindow(from, until) else DangerWindow(minOf(old.from, from), maxOf(old.until, until))
        }
        state.flames.forEach { mark(it.tile, state.time, it.endsAt) }
        val bombs = (state.bombs + listOfNotNull(extra)).associateBy { it.tile }.toMutableMap()
        // When each bomb really goes off: its own fuse, or sooner when a blast that reaches it goes off first.
        val goesOff = bombs.mapValues { it.value.explodesAt }.toMutableMap()
        val withExtra = if (extra == null) state else state.copy(bombs = bombs.values.toList())
        val reaches = bombs.mapValues { (tile, bomb) -> BlastGrid.reach(withExtra, tile, bomb.radius) }
        var changed = true
        while (changed) {
            changed = false
            reaches.forEach { (tile, reach) ->
                val at = goesOff.getValue(tile)
                reach.bombs.forEach { other ->
                    if (goesOff.getValue(other) > at) {
                        goesOff[other] = at
                        changed = true
                    }
                }
            }
        }
        val flameMs = state.mandatory.flameMs
        reaches.forEach { (tile, reach) ->
            val at = goesOff.getValue(tile)
            reach.fire.forEach { (fireTile, _) -> mark(fireTile, at, at + flameMs) }
        }
        return windows
    }
}
