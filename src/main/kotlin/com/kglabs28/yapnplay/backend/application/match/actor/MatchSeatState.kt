package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.SeatPresence

/**
 * One seat as the match actor sees it. [userId] stays the owner's while
 * the bot holds the seat ([SeatPresence.BOT]) so it is still theirs when
 * they come back; [left] means the owner gave it up.
 */
data class MatchSeatState(
    val seat: Int,
    val userId: String,
    val displayName: String,
    val level: Int,
    val colour: Colour,
    val cpu: Boolean,
    val micOn: Boolean = false,
    val autoplay: Boolean = false,
    val presence: SeatPresence = SeatPresence.PLAYING,
    val connected: Boolean = true,
    val left: Boolean = false,
    val removed: Boolean = false,
    /** When the connection dropped (epoch millis); away after the grace period. */
    val disconnectedAt: Long? = null,
) {
    /** The server plays this seat right away instead of waiting for the turn timer. */
    val playedByServer: Boolean get() = cpu || left || autoplay || presence != SeatPresence.PLAYING
}
