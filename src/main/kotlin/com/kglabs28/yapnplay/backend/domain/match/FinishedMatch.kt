package com.kglabs28.yapnplay.backend.domain.match

/** A match that just ended, for results and XP. Times are epoch millis. */
data class FinishedMatch(
    val matchId: String,
    val gameId: String,
    val roomId: String?,
    val startedAt: Long,
    val endedAt: Long,
    val seats: List<FinishedSeat>,
    /** The pot: fee per player, who paid, and % by place (empty when free). */
    val entryCoins: Int = 0,
    val paid: List<String> = emptyList(),
    val potSplit: List<Int> = emptyList(),
)

/** [userId] is null for a CPU seat. [place] is 1-based. */
data class FinishedSeat(
    val seat: Int,
    val userId: String?,
    val place: Int,
    /** Left the match before it ended: no XP, no winnings. */
    val left: Boolean = false,
    /** Still at the table at the end, playing for themselves (not left, removed or bot-held): earns completion coins. */
    val completed: Boolean = false,
    /** XP this seat earned (0 for CPUs and leavers), decided when the match ended. */
    val xp: Int = 0,
    /** The name and avatar the seat played under. */
    val displayName: String = "",
    val avatarId: String = "",
    /** Coins as the result showed them: the place's pot share, the finishing coins, and the entry staked. */
    val coinsWon: Int = 0,
    val completionCoins: Int = 0,
    val stake: Int = 0,
)
