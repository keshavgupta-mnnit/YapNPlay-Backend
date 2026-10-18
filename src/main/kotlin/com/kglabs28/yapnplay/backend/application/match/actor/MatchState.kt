package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.match.MatchSetup
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.contract.SeatPresence

/**
 * One match's live state, owned by its actor (one worker, no locks).
 * Turn bookkeeping follows the contract's idle and away rules.
 */
class MatchState(
    val setup: MatchSetup,
    val session: GameSession,
    val seats: MutableList<MatchSeatState>,
    val startedAt: Long,
) {
    val matchId: String get() = setup.matchId
    val roomId: String? get() = setup.roomId
    val spectators = LinkedHashMap<String, MatchSpectator>()
    var version = 0L
    /** Turn deadline (epoch millis), or null before the first one. */
    var deadline: Long? = null
    /** How long the table animates the last real play. */
    var pauseMs = 0L
    var offer: SeatOfferState? = null
    /** Spectators who passed on the current handover. */
    val declined = mutableSetOf<String>()
    val awaySince = mutableMapOf<Int, Long>()
    val missedTurns = mutableMapOf<Int, Int>()
    /** Whole turns in a row the bot played for a seat on timeout. */
    val idleTurns = mutableMapOf<Int, Int>()
    var turnOwner = session.currentSeat
    var turnTimedOut = false
    var turnActed = false
    @Volatile
    var ended = false

    val onHold: Boolean get() = offer?.stage?.holdsMatch == true

    fun seat(seat: Int): MatchSeatState = seats.first { it.seat == seat }

    /** The seat [userId] plays, if they still hold one. */
    fun seatOf(userId: String): MatchSeatState? = seats.firstOrNull { !it.cpu && !it.left && it.userId == userId }

    fun update(seat: Int, change: MatchSeatState.() -> MatchSeatState) {
        val index = seats.indexOfFirst { it.seat == seat }
        if (index >= 0) seats[index] = seats[index].change()
    }

    fun setPresence(seat: Int, presence: SeatPresence) = update(seat) { copy(presence = presence) }

    /** Everyone who receives snapshots: seated humans and spectators. */
    fun viewers(): Set<String> =
        seats.filter { !it.cpu && !it.left }.map { it.userId }.toSet() + spectators.keys

    /** Seats the server plays fully (CPU bids in City Bazaar auctions). */
    fun serverSeats(): Set<Int> = seats.filter { it.playedByServer }.map { it.seat }.toSet()
}
