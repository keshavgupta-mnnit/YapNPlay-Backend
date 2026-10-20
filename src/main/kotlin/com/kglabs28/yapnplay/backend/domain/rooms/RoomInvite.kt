package com.kglabs28.yapnplay.backend.domain.rooms

/** [invitedBy] asked [userId] to join room [roomId] (they are friends). */
data class RoomInvite(
    val id: String,
    val roomId: String,
    val userId: String,
    val invitedBy: String,
    val createdAt: Long,
)

/** Stores room invites. */
interface RoomInviteRepository {
    /** False when [invite]'s user already has an invite to that room. */
    suspend fun insert(invite: RoomInvite): Boolean
    suspend fun findById(inviteId: String): RoomInvite?
    /** Invites waiting for [userId], newest first. */
    suspend fun forUser(userId: String): List<RoomInvite>
    /** Who has an open invite to [roomId]. */
    suspend fun invitedTo(roomId: String): Set<String>
    suspend fun delete(inviteId: String)
    /** Drops [userId]'s invite to [roomId], if any (they joined another way). */
    suspend fun deleteFor(roomId: String, userId: String)
}
