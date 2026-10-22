package com.kglabs28.yapnplay.backend.application.match.games.mono

import com.kglabs28.yapnplay.backend.contract.mono.MonoContinueVoteWire
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent

/**
 * The MONO continue vote (both MONO games): after the 2nd and every later
 * seat finishes while 2+ seats still hold cards, the humans at the table
 * vote to keep playing or end, and play stands still meanwhile. It is
 * requested by the session and opened on the next tick (only the match
 * knows who is human); it closes at [endsAt] or once everyone voted. The
 * votes cast decide (a tie keeps playing; nobody voting ends the match).
 */
class MonoContinueVote(private val voteMs: Long) {
    /** Asked for, not yet opened. */
    var pending = false
        private set
    var voters: Set<Int> = emptySet()
        private set
    var endsAt = 0L
        private set
    private val yes = mutableSetOf<Int>()
    private val no = mutableSetOf<Int>()

    val isOpen: Boolean get() = endsAt > 0
    /** Asked for or running: play waits. */
    val active: Boolean get() = pending || isOpen
    val allVoted: Boolean get() = (yes + no).containsAll(voters)
    val continues: Boolean get() = yes.isNotEmpty() && yes.size >= no.size

    fun request() {
        pending = true
    }

    /** Opens the vote for [voters]; its clock starts at [from] (now, or after the table's animation). */
    fun open(from: Long, voters: Set<Int>) {
        pending = false
        this.voters = voters
        endsAt = from + voteMs
        yes.clear()
        no.clear()
    }

    /** Records [seat]'s vote; false when they may not vote or already did. */
    fun vote(seat: Int, keepPlaying: Boolean): Boolean {
        if (!isOpen || seat !in voters || seat in yes || seat in no) return false
        if (keepPlaying) yes += seat else no += seat
        return true
    }

    /** Closes the vote: the result to send. */
    fun close(): MonoContinueVoted {
        val result = MonoContinueVoted(continues, yes.size, voters.size)
        pending = false
        endsAt = 0
        voters = emptySet()
        yes.clear()
        no.clear()
        return result
    }

    companion object {
        /** Quick Play and rooms of up to [BIG_ROOM] seats vote from the 2nd finisher. */
        const val MIN_FINISHERS = 2
        private const val BIG_ROOM = 4
        private const val BIG_ROOM_FINISHERS = 3

        /** Finishers before the first vote: 3 in a room match of more than 4 seats, else 2. */
        fun finishersBeforeVote(seatCount: Int, room: Boolean): Int = if (room && seatCount > BIG_ROOM) BIG_ROOM_FINISHERS else MIN_FINISHERS
    }

    fun wire(): MonoContinueVoteWire? = if (isOpen) MonoContinueVoteWire(endsAt, voters.sorted(), yes.sorted(), no.sorted()) else null
}

/** A player's continue vote (`{"type":"VoteContinue","keepPlaying":…}`). */
data class MonoVoteContinue(val keepPlaying: Boolean) : Action

/** The continue vote closed (sent as `ContinueVoted`). */
data class MonoContinueVoted(val continued: Boolean, val yes: Int, val voters: Int) : GameEvent
