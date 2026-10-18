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
)
