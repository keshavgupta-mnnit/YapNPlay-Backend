package com.kglabs28.yapnplay.backend.domain.rooms

/** Stores rooms and memberships. */
interface PlayRoomRepository {
    /** Inserts the room and its creator's membership; false when the code is taken. */
    suspend fun insertWithCreator(room: PlayRoom, creatorJoinedAt: Long): Boolean
    suspend fun findById(roomId: String): PlayRoom?
    suspend fun findByCode(code: String): PlayRoom?
    suspend fun members(roomId: String): List<PlayRoomMembership>
    suspend fun roomsOf(userId: String): List<PlayRoom>
    suspend fun membershipCount(userId: String): Int
    suspend fun memberCount(roomId: String): Int
    suspend fun addMember(roomId: String, userId: String, joinedAt: Long)
    suspend fun removeMember(roomId: String, userId: String)
    suspend fun updateRoles(roomId: String, leaderId: String, adminId: String)
    suspend fun updateGame(roomId: String, selectedGame: String?)
    suspend fun updateBotFill(roomId: String, botFill: Boolean)
    suspend fun delete(roomId: String)
}
