package com.kglabs28.yapnplay.backend.domain.economy

import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat

/**
 * A match pot: every paid seat's entry fee, plus the bank paying for CPU
 * seats, shared by finishing place. No fee, no prize; leavers get nothing.
 */
object PotRules {
    private const val PERCENT = 100

    fun total(entryCoins: Int, paidCount: Int, cpuCount: Int): Int = entryCoins * (paidCount + cpuCount)

    /** Coins each paying player won, by user id (only winners appear). */
    fun payouts(
        entryCoins: Int,
        paid: Set<String>,
        seats: List<FinishedSeat>,
        /** % of the pot by place (1st first), fixed when the match opened. */
        split: List<Int>,
    ): Map<String, Int> {
        if (entryCoins <= 0 || paid.isEmpty()) return emptyMap()
        val pot = total(entryCoins, paid.size, seats.count { it.userId == null })
        return seats.mapNotNull { seat ->
            val userId = seat.userId ?: return@mapNotNull null
            val percent = split.getOrNull(seat.place - 1) ?: return@mapNotNull null
            if (seat.left || userId !in paid || percent == 0) null else userId to pot * percent / PERCENT
        }.toMap()
    }
}
