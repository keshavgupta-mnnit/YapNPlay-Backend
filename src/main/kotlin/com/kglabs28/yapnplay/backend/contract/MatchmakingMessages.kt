package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.Serializable

// ══════════════════════════════════════════════════════════════════════════
// Matchmaking channel — public Quick Play
//
// Flow: JoinQueue → QueueStatus (repeated as players arrive) → MatchFound
// → the match continues on the MATCH channel (MatchSnapshot, …).
// The server starts early when the game's maxPlayers humans are queued;
// at searchDeadline it starts with the humans it has and fills the empty
// seats with CPU players if the game's MatchmakingConfig allows it.
// A user can be in at most one queue, room countdown or match at a time.
// ══════════════════════════════════════════════════════════════════════════

/** Ask to be matched into a public game of [gameId]. */
@Serializable
data class JoinQueue(
    val gameId: String,
    /** 0.4.5: the league's entry fee (GET /coins/leagues); players only meet others in the same league. */
    val entryCoins: Int = 100,
)

/** Stop searching. Ignored if the match was already formed. */
@Serializable
data object LeaveQueue

/**
 * Search progress, sent on joining and whenever the count changes.
 * [searchDeadline] is absolute server time (epoch millis); show
 * `searchDeadline - serverTime` as the countdown.
 */
@Serializable
data class QueueStatus(
    val gameId: String,
    val playersFound: Int,
    val playersNeeded: Int,
    val searchDeadline: Long,
    val serverTime: Long,
    /** 0.4.5: the league being searched. */
    val entryCoins: Int = 0,
)

/** The match is formed; open it and wait for its MatchSnapshot. */
@Serializable
data class MatchFound(
    val matchId: String,
    val gameId: String,
    val humanPlayers: Int,
    val cpuPlayers: Int,
    /** 0.4.5: the fee each player paid (already taken); the pot is this × seats. */
    val entryCoins: Int = 0,
)

/** Searching ended without a match (e.g. too few humans and no CPU fill). */
@Serializable
data class QueueCancelled(
    val gameId: String,
    val reason: QueueCancelReason,
)

enum class QueueCancelReason {
    NOT_ENOUGH_PLAYERS,
    GAME_UNAVAILABLE,
    LEFT_QUEUE,
}
