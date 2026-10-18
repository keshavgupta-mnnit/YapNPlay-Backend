package com.kglabs28.yapnplay.backend.domain.common

/** The current time in epoch milliseconds. Injected so tests control time. */
fun interface Clock {
    fun nowMillis(): Long
}
