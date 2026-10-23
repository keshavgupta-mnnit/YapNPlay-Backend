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
    /** How long the table animates the last real play (and, on a new turn, the gap before it). */
    var pauseMs = 0L
    /**
     * When the seat the server plays makes its move: after the animation and
     * its think time (picked once per step, so a replan never re-rolls it);
     * null when a person holds the turn.
     */
    var botWakeAt: Long? = null
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

    /** The keep-playing vote while the game waits at its limit (not saved: a restart opens a new one). */
    var extensionVote: ExtensionVoteState? = null

    /** Humans who have not opened the table yet; play waits for them until [startHoldUntil]. */
    val waitingFor = mutableSetOf<String>()
    /** While set (epoch millis), the match has not started: no turns, no timers. */
    var startHoldUntil: Long? = null

    /** A player asked to end the match: play is paused until it is decided (not saved: a restart drops it). */
    var endVote: EndVoteState? = null
    /** When each seat last asked to end the match (rate limit). */
    val lastEndProposal = mutableMapOf<Int, Long>()

    val onHold: Boolean get() = startHoldUntil != null || offer?.stage?.holdsMatch == true || endVote != null

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

    /** Seats with no human at them: CPUs, players who left, seats handed to the bot. They never vote. */
    fun botSeats(): Set<Int> = seats.filter { it.cpu || it.left || it.presence == SeatPresence.BOT }.map { it.seat }.toSet()
}
