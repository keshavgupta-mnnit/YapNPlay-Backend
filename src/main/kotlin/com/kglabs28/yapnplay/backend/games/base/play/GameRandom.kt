package com.kglabs28.yapnplay.backend.games.base.play

/**
 * Random number generator interface so tests can fix the sequence.
 * Production code uses [DefaultGameRandom]; tests use a scripted implementation.
 */
interface GameRandom {
    /**
     * Returns a pseudo-random integer in [0, bound).
     * @param bound must be positive
     */
    fun nextInt(bound: Int): Int
}

/**
 * Production randomness backed by kotlin.random.Random.
 */
class DefaultGameRandom : GameRandom {
    override fun nextInt(bound: Int): Int = kotlin.random.Random.nextInt(bound)
}
