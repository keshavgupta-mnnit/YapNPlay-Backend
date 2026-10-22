package com.kglabs28.yapnplay.backend.domain.economy

import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat

/**
 * A match pot: every paid seat's entry fee, plus the bank paying for CPU
 * seats, shared by finishing place. No fee, no prize; leavers get nothing.
 */
object PotRules {
    private const val PERCENT = 100

    fun total(entryCoins: Int, paidCount: Int, cpuCount: Int): Int = entryCoins * (paidCount + cpuCount)

    /**
     * What finishing in [place] is worth (the same for a human or a CPU);
     * 0 when the match is free or nobody paid.
     */
    fun placeShare(entryCoins: Int, paidCount: Int, cpuCount: Int, split: List<Int>, place: Int): Int {
        if (entryCoins <= 0 || paidCount == 0) return 0
        val percent = split.getOrNull(place - 1) ?: return 0
        return total(entryCoins, paidCount, cpuCount) * percent / PERCENT
    }

    /** Coins each paying player won, by user id (only winners appear). */
    fun payouts(
        entryCoins: Int,
        paid: Set<String>,
        seats: List<FinishedSeat>,
        /** % of the pot by place (1st first), fixed when the match opened. */
        split: List<Int>,
    ): Map<String, Int> {
        if (entryCoins <= 0 || paid.isEmpty()) return emptyMap()
        val cpus = seats.count { it.userId == null }
        return seats.mapNotNull { seat ->
            val userId = seat.userId ?: return@mapNotNull null
            val share = placeShare(entryCoins, paid.size, cpus, split, seat.place)
            if (seat.left || userId !in paid || share == 0) null else userId to share
        }.toMap()
    }
}
