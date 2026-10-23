package com.kglabs28.yapnplay.backend.application.rooms

import java.util.concurrent.ConcurrentHashMap

/**
 * Which room each user is present in right now (at most one). Only room
 * actors write it; everyone else reads.
 */
class RoomPresence {
    private val roomByUser = ConcurrentHashMap<String, String>()

    fun roomOf(userId: String): String? = roomByUser[userId]

    fun enter(userId: String, roomId: String) {
        roomByUser[userId] = roomId
    }

    /** Clears the entry only if it still points at [roomId]. */
    fun exit(userId: String, roomId: String) {
        roomByUser.remove(userId, roomId)
    }

    fun onlineCount(roomId: String): Int = roomByUser.values.count { it == roomId }

    fun usersIn(roomId: String): Set<String> = roomByUser.filterValues { it == roomId }.keys

    /** Rooms with at least one person present (admin dashboard). */
    fun activeRoomCount(): Int = roomByUser.values.toSet().size

    /** Everyone present in a room (admin dashboard). */
    fun presentUsers(): Set<String> = roomByUser.keys.toSet()

    /** People present in any room (admin dashboard). */
    fun presentUserCount(): Int = roomByUser.size
}
