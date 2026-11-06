package com.kglabs28.yapnplay.backend.games.cyberblast

/**
 * Bombs going off: each one whose fuse ran out, and every bomb its fire
 * reaches (chains) at the same moment. Fire stops at walls, breaks the
 * first blocker each way (+points to the bomb's owner) and burns any
 * power-up it touches.
 */
object BlastExplosions {

    fun detonate(state: CyberBlastState): Pair<CyberBlastState, BlastBoom?> {
        val now = state.time
        val queue = ArrayDeque(state.bombs.filter { it.explodesAt <= now }.sortedBy { it.explodesAt }.map { it.tile })
        if (queue.isEmpty()) return state to null
        val gone = mutableListOf<BlastBomb>()
        val fire = mutableMapOf<Int, BlastFlame>()
        val broken = linkedMapOf<Int, Int>()
        while (queue.isNotEmpty()) {
            val bomb = state.bombAt(queue.removeFirst()) ?: continue
            if (gone.any { it.id == bomb.id }) continue
            gone += bomb
            val reach = BlastGrid.reach(state, bomb.tile, bomb.radius)
            reach.fire.forEach { (tile, shape) ->
                val old = fire[tile]
                // The centre of a blast wins over an arm passing through.
                if (old == null || shape == FlameShape.CENTRE) fire[tile] = BlastFlame(tile, bomb.owner, shape, now + state.mandatory.flameMs)
            }
            reach.crates.forEach { if (it !in broken) broken[it] = bomb.owner }
            queue.addAll(reach.bombs)
        }
        val points = state.mandatory.cratePoints
        val gained = broken.values.groupingBy { it }.eachCount()
        val players = state.players.map { p -> gained[p.seat]?.let { p.copy(score = p.score + it * points) } ?: p }
        val goneIds = gone.map { it.id }.toSet()
        val flames = state.flames.filter { it.tile !in fire } + fire.values
        val next = state.copy(
            players = players,
            bombs = state.bombs.filter { it.id !in goneIds },
            crates = state.crates - broken.keys,
            flames = flames,
            items = state.items.filter { it.tile !in fire },
        )
        return next to BlastBoom(gone.map { it.tile }, broken.keys.toList())
    }

    /** Fire that has burnt out goes. */
    fun burnOut(state: CyberBlastState): CyberBlastState =
        if (state.flames.none { it.endsAt <= state.time }) state else state.copy(flames = state.flames.filter { it.endsAt > state.time })
}
