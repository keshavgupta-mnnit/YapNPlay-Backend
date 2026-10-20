package com.kglabs28.yapnplay.backend.application.match.actor

/**
 * The keep-playing vote at a game's round or time limit (any game). [voters]
 * are the humans still in whom the server isn't playing for; it closes at
 * [endsAt] or once everyone voted. Continue when at least half say yes; a
 * missing vote counts as "end".
 */
class ExtensionVoteState(
    val voters: Set<Int>,
    val endsAt: Long,
) {
    val yes = mutableSetOf<Int>()
    val no = mutableSetOf<Int>()

    val allVoted: Boolean get() = (yes + no).containsAll(voters)

    val continues: Boolean get() = voters.isNotEmpty() && yes.size * 2 >= voters.size

    /** Records [seat]'s vote; false when they may not vote or already did. */
    fun vote(seat: Int, keepPlaying: Boolean): Boolean {
        if (seat !in voters || seat in yes || seat in no) return false
        if (keepPlaying) yes += seat else no += seat
        return true
    }
}
