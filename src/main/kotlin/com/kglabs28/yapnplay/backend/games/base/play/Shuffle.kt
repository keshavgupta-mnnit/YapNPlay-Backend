package com.kglabs28.yapnplay.backend.games.base.play

/**
 * A deterministic shuffle: the given [random] picks every swap, so the
 * same state and the same random always give the same deck. Shared by
 * every game engine that shuffles, so each one does not carry its own.
 */
fun <T> List<T>.shuffled(random: GameRandom): List<T> {
    val result = toMutableList()
    for (index in result.size - 1 downTo 1) {
        val swapWith = random.nextInt(index + 1)
        val held = result[index]
        result[index] = result[swapWith]
        result[swapWith] = held
    }
    return result
}