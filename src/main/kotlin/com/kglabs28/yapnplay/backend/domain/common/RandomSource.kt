package com.kglabs28.yapnplay.backend.domain.common

/**
 * Random numbers for codes and names. Production uses a secure source;
 * tests inject a seeded one.
 */
fun interface RandomSource {
    /** A uniform random int in [from, untilExclusive). */
    fun nextInt(from: Int, untilExclusive: Int): Int
}
