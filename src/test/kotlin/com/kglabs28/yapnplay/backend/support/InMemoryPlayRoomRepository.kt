package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomMembership
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/** A [PlayRoomRepository] in memory, for room actor tests without a database. */
class InMemoryPlayRoomRepository(private val names: (String) -> String = { it }) : PlayRoomRepository {
    val rooms = mutableMapOf<String, PlayRoom>()
    val members = mutableMapOf<String, MutableMap<String, Long>>()

    override suspend fun insertWithCreator(room: PlayRoom, creatorJoinedAt: Long): Boolean {
        if (rooms.values.any { it.code == room.code }) return false
        rooms[room.id] = room
        members[room.id] = mutableMapOf(room.createdBy to creatorJoinedAt)
        return true
    }

    override suspend fun findById(roomId: String) = rooms[roomId]
    override suspend fun findByCode(code: String) = rooms.values.firstOrNull { it.code == code }

    override suspend fun members(roomId: String) =
        members[roomId].orEmpty().map { (user, joined) -> PlayRoomMembership(roomId, user, names(user), joined) }

    override suspend fun roomsOf(userId: String) = rooms.values.filter { userId in members[it.id].orEmpty() }
    override suspend fun membershipCount(userId: String) = members.values.count { userId in it }
    override suspend fun memberCount(roomId: String) = members[roomId].orEmpty().size

    override suspend fun addMember(roomId: String, userId: String, joinedAt: Long) {
        members.getValue(roomId)[userId] = joinedAt
    }

    override suspend fun removeMember(roomId: String, userId: String) {
        members[roomId]?.remove(userId)
    }

    override suspend fun updateRoles(roomId: String, leaderId: String, adminId: String) {
        rooms[roomId] = rooms.getValue(roomId).copy(leaderId = leaderId, adminId = adminId)
    }

    override suspend fun updateGame(roomId: String, selectedGame: String?) {
        rooms[roomId] = rooms.getValue(roomId).copy(selectedGame = selectedGame)
    }

    override suspend fun updateBotFill(roomId: String, botFill: Boolean) {
        rooms[roomId] = rooms.getValue(roomId).copy(botFill = botFill)
    }

    override suspend fun delete(roomId: String) {
        rooms.remove(roomId)
        members.remove(roomId)
    }
}
