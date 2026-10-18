package com.kglabs28.yapnplay.backend.games.base.rules

/**
 * Timing values that each game declares.
 * Injected into the game engine; never read from a clock directly.
 */
data class TimingConfig(
    /** Seconds per turn before autoplay kicks in. */
    val turnSeconds: Long = 30,
    /** Seconds to wait when the current actor is disconnected. */
    val disconnectedWaitSeconds: Long = 5,
    /** Consecutive missed turns before a seat is removed. */
    val awayAfterMisses: Int = 3,
    /** Seconds of continuous disconnection before a seat is removed. */
    val awayAfterDisconnectSeconds: Long = 60,
)
