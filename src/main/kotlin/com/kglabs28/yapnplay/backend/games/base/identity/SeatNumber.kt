package com.kglabs28.yapnplay.backend.games.base.identity

import kotlinx.serialization.Serializable

/**
 * A seat number within a match. Starts at 1.
 */
@Serializable
@JvmInline
value class SeatNumber(val value: Int) {
    init {
        require(value >= 1) { "SeatNumber must be >= 1, was $value" }
    }
}
