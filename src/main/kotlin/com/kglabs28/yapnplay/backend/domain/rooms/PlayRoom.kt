package com.kglabs28.yapnplay.backend.domain.rooms

/**
 * The stored part of a room. Live state (who is online, ready, the
 * countdown) lives only in memory, in the room's actor.
 */
data class PlayRoom(
    val id: String,
    val code: String,
    val name: String,
    val description: String?,
    val icon: String,
    val createdBy: String,
    val leaderId: String,
    val adminId: String,
    val selectedGame: String?,
    val botFill: Boolean,
    val createdAt: Long,
    /** The room's league: every player's entry (coins) for its matches. */
    val entryCoins: Int = DEFAULT_ROOM_ENTRY,
)

/** A new Game Room's league (coins). */
const val DEFAULT_ROOM_ENTRY = 100
