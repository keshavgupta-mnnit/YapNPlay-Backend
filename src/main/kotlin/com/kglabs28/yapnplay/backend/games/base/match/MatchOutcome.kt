package com.kglabs28.yapnplay.backend.games.base.match

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber

/**
 * Final ranking of a match.
 */
data class MatchOutcome(
    val ranking: List<RankedSeat>,
)

data class RankedSeat(
    val seat: SeatNumber,
    val status: SeatStatus,
    /** 1-based finishing place. */
    val place: Int,
)

enum class SeatStatus {
    FINISHED,
    REMOVED,
}
