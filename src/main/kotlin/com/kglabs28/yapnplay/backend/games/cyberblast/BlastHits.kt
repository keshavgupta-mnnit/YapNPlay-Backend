package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Players caught in fire (or under a closing wall) lose a heart. Points:
 * the bomb's owner +100 and 1 tile more reach for each hit in a row (a lost heart starts it again at 1); the one hit −25, or −15 for
 * their own bomb; a wall costs a heart but no points. With hearts left they
 * come back on a safe tile, shielded a moment; with none they're out.
 */
object BlastHits {

    fun burn(state: CyberBlastState, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        var s = state
        val events = mutableListOf<BlastEvent>()
        state.alive.filter { !it.shielded(state.time) }.forEach { p ->
            val flame = s.flames.firstOrNull { it.tile == p.tile } ?: return@forEach
            val (next, hit) = hit(s, p.seat, flame.owner, crushed = false, random)
            s = next
            events += hit
        }
        return s to events
    }

    /** [victim] loses a heart to [by] ([crushed]: a closing wall, no points). */
    fun hit(state: CyberBlastState, victim: Int, by: Int, crushed: Boolean, random: GameRandom): Pair<CyberBlastState, List<BlastEvent>> {
        val rules = state.mandatory
        val self = by == victim
        val victimPoints = when {
            crushed -> 0
            self -> -rules.selfPenalty
            else -> -rules.hitPenalty
        }
        val byPoints = if (crushed || self) 0 else rules.hitPoints
        var s = state.copy(players = state.players.map { p ->
            when {
                // Losing a heart ends the streak: back to the starting reach.
                p.seat == victim -> p.copy(lives = p.lives - 1, score = p.score + victimPoints, held = null, boostUntil = 0L, streak = 0, radius = rules.startRadius)
                p.seat == by && byPoints > 0 -> p.copy(
                    score = p.score + byPoints,
                    hits = p.hits + 1,
                    streak = p.streak + 1,
                    radius = (rules.startRadius + (p.streak + 1) * rules.radiusPerHit).coerceAtMost(rules.maxRadius),
                )
                else -> p
            }
        })
        val after = s.player(victim) ?: return s to emptyList()
        val events = mutableListOf<BlastEvent>(BlastHit(victim, by, after.lives, victimPoints, byPoints, self, crushed))
        if (after.lives > 0) {
            val (carved, tile) = BlastSpawns.respawn(s, victim, random)
            s = carved.copy(players = carved.players.map {
                if (it.seat == victim) it.copy(x = BlastGrid.centreX(tile), y = BlastGrid.centreY(tile), shieldUntil = s.time + rules.shieldMs) else it
            })
        } else {
            s = s.copy(outOrder = s.outOrder + victim)
            events += BlastOut(victim)
        }
        return s to events
    }
}
