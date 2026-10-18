package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequest
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequestRepository

/** Waiting Lists in memory, for room actor tests. */
class InMemoryJoinRequestRepository : JoinRequestRepository {
    val requests = mutableListOf<JoinRequest>()

    override suspend fun add(roomId: String, userId: String, at: Long) {
        if (requests.none { it.roomId == roomId && it.userId == userId }) requests += JoinRequest(roomId, userId, userId, at)
    }

    override suspend fun remove(roomId: String, userId: String) {
        requests.removeAll { it.roomId == roomId && it.userId == userId }
    }

    override suspend fun listFor(roomId: String) = requests.filter { it.roomId == roomId }
}
