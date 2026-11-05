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
    /**
     * Quick Play with friends: one leader (who is also the admin), anyone
     * invites friends, no joining by code, and the room is deleted once
     * nobody is left in its lobby. Never listed or counted as a Game Room.
     */
    val temporary: Boolean = false,
)

/** A new Game Room's league (coins). */
const val DEFAULT_ROOM_ENTRY = 100
