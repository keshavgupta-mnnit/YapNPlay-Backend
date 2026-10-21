package com.kglabs28.yapnplay.backend.application.match.actor

/**
 * A player asked to end the match early (any game that allows it). Play
 * pauses until it is decided. [voters] are the humans still in (never CPUs
 * or leavers), the proposer counting as a yes. It ends the match when more
 * than half of them say yes; a missing vote counts as no. It closes at
 * [endsAt] or once everyone voted.
 */
class EndVoteState(
    val proposerSeat: Int,
    val voters: Set<Int>,
    val startedAt: Long,
    val endsAt: Long,
) {
    val yes = mutableSetOf(proposerSeat)
    val no = mutableSetOf<Int>()

    val allVoted: Boolean get() = (yes + no).containsAll(voters)

    val ends: Boolean get() = yes.size * 2 > voters.size

    /** No more "yes" votes could make a majority: decide now. */
    val cannotPass: Boolean get() = (voters.size - no.size) * 2 <= voters.size

    /** Records [seat]'s vote; false when they may not vote or already did. */
    fun vote(seat: Int, agree: Boolean): Boolean {
        if (seat !in voters || seat in yes || seat in no) return false
        if (agree) yes += seat else no += seat
        return true
    }
}
