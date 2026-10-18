package com.kglabs28.yapnplay.backend.application.match.actor

/** A seat changing hands; [endsAt] and [startedAt] epoch millis. */
data class SeatOfferState(
    val seat: Int,
    val leaverName: String,
    val stage: OfferStage,
    val endsAt: Long,
    val startedAt: Long,
)

/**
 * Where a handover stands (contract "Presence and seat handover"). Play
 * pauses only while someone decides on a seat for someone else.
 */
sealed interface OfferStage {
    val holdsMatch: Boolean

    /** "Still here?" after idle turns; play goes on. */
    data class ConfirmingAbsence(val userId: String, val name: String) : OfferStage {
        override val holdsMatch = false
    }

    /** A named spectator may accept; round 1 the leaver's pick, round 2 the leader's. */
    data class ToSpectator(val userId: String, val name: String, val round: Int) : OfferStage {
        override val holdsMatch = true
    }

    /** The leader (or whoever stands in) picks a spectator or the bot. */
    data class LeaderChoosing(val userId: String, val name: String) : OfferStage {
        override val holdsMatch = true
    }

    /** The owner is away: any spectator may take it while the bot plays. */
    data object OpenToAll : OfferStage {
        override val holdsMatch = false
    }
}
