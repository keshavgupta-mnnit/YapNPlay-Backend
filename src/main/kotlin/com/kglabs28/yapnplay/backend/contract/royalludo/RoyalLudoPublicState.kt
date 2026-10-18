package com.kglabs28.yapnplay.backend.contract.royalludo

import kotlinx.serialization.Serializable

/**
 * `MatchSnapshot.publicState` for Royal Ludo. Piece progress per seat:
 * -1 base, 0..50 shared track, 51..55 home column, 56 finished.
 */
@Serializable
data class RoyalLudoPublicState(
    /** Seat → progress of its four pieces. */
    val pieces: Map<Int, List<Int>>,
    /** ROLL, MOVE or FINISHED. */
    val phase: String,
    val lastRoll: Int? = null,
    /** Pieces the viewer may move now (empty unless it is the viewer's move). */
    val legalPieces: List<Int> = emptyList(),
    /** Seats that brought every piece home, in order, during the match. */
    val finishedSeats: List<Int> = emptyList(),
    /** Seats from first to last once the match is over. */
    val ranking: List<Int> = emptyList(),
)
