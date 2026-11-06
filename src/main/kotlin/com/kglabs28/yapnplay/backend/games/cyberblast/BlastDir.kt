package com.kglabs28.yapnplay.backend.games.cyberblast

/** The four ways a player walks (and a blast reaches). */
enum class BlastDir(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    ;

    val horizontal: Boolean get() = dy == 0

    companion object {
        fun of(name: String?): BlastDir? = entries.firstOrNull { it.name == name }
    }
}
