package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Speed boots: every so often a pair shows up on a random free tile with
 * no fire coming, lies there a while and vanishes. Walking onto them gives
 * 30 s of extra speed (picking up more starts the 30 s again).
 */
object BlastPowerUps {

    fun update(state: CyberBlastState, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        val rules = state.mandatory
        val now = state.time
        var s = state.copy(items = state.items.filter { it.expiresAt > now })
        val events = mutableListOf<BlastEvent>()
        // Pick-ups.
        s.alive.forEach { p ->
            val item = s.items.firstOrNull { it.tile == p.tile } ?: return@forEach
            s = s.copy(
                items = s.items - item,
                players = s.players.map { if (it.seat == p.seat) it.copy(boostUntil = now + rules.boostMs) else it },
            )
            events += BlastBoosted(p.seat)
        }
        if (now >= s.nextItemAt) {
            s = s.copy(nextItemAt = now + rules.itemEveryMs + if (rules.itemJitterMs > 0) random.nextInt(rules.itemJitterMs.toInt() + 1) else 0)
            if (s.items.size < rules.maxItems) place(s, random)?.let { s = it }
        }
        return s to events
    }

    private fun place(state: CyberBlastState, random: GameRandom): CyberBlastState? {
        val danger = BlastDanger.of(state)
        val standing = state.alive.map { it.tile }.toSet()
        val free = BlastGrid.tiles(state).filter {
            BlastGrid.isOpen(state, it) && it !in danger && it !in standing && state.items.none { item -> item.tile == it }
        }
        if (free.isEmpty()) return null
        val tile = free[random.nextInt(free.size)]
        return state.copy(items = state.items + BlastItem(tile, BlastItemKind.SPEED, state.time + state.mandatory.itemLifeMs))
    }
}
