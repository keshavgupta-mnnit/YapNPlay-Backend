package com.kglabs28.yapnplay.backend.games.cyberblast.bot

/** One thing a bot could do: walk to [tile] ([steps] away), or drop a bomb where it stands ([bomb]). */
data class BlastGoal(val tile: Int, val steps: Int, val bomb: Boolean) {
    override fun toString(): String = if (bomb) "bomb@$tile" else "go@$tile($steps)"
}
