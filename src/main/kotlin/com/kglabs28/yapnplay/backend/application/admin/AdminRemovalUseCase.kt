package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.admin.AdminDirectory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import kotlinx.serialization.Serializable

/**
 * The dashboard's Rooms tab and the two deletes (players, rooms). Nothing is
 * removed while it is in a live match. Members leave through the room
 * itself, so everyone still in the room hears about it as on a normal leave.
 */
class AdminRemovalUseCase(
    private val directory: AdminDirectory,
    private val rooms: PlayRoomRepository,
    private val actors: PlayRoomActors,
    private val matches: MatchService,
    private val audit: AdminAuditRepository,
    private val clock: Clock,
) {
    suspend fun rooms(query: String): List<AdminRoomView> = directory.searchRooms(query, ROOM_LIMIT).map {
        AdminRoomView(it.roomId, it.code, it.name, it.description, it.selectedGame, it.members, it.leaderName, it.adminName, it.createdAt)
    }

    /** Deletes a player for good: they leave every room first, then their data goes. */
    suspend fun deleteUser(userId: String, by: String, note: String?) {
        val name = directory.searchUsers(userId, clock.nowMillis(), 1).firstOrNull { it.userId == userId }?.displayName
            ?: throw ServiceException(ErrorCode.NOT_FOUND, "No such player")
        if (matches.isPlaying(userId)) throw ServiceException(ErrorCode.BAD_REQUEST, "$name is in a live match: try again after it ends")
        rooms.roomsOf(userId).forEach { room -> actors.get(room.id)?.ask(userId, RoomAction.Leave) }
        if (!directory.deleteUser(userId)) throw ServiceException(ErrorCode.NOT_FOUND, "No such player")
        audit.record(AdminAuditEntry(DELETE_USER, userId, name, null, by, note, clock.nowMillis()))
    }

    /** Deletes a room: the room's admin removes everyone, then leaves, which deletes it. */
    suspend fun deleteRoom(roomId: String, by: String, note: String?) {
        val room = rooms.findById(roomId) ?: throw ServiceException(ErrorCode.NOT_FOUND, "No such room")
        if (matches.runningMatchIn(roomId) != null) throw ServiceException(ErrorCode.BAD_REQUEST, "${room.name} has a live match: try again after it ends")
        val actor = actors.get(roomId) ?: throw ServiceException(ErrorCode.NOT_FOUND, "No such room")
        rooms.members(roomId).filter { it.userId != room.adminId }.forEach { member ->
            actor.ask(room.adminId, RoomAction.RemoveMember(member.userId)).failOnError()
        }
        actor.ask(room.adminId, RoomAction.Leave).failOnError()
        audit.record(AdminAuditEntry(DELETE_ROOM, roomId, "${room.name} (${room.code})", null, by, note, clock.nowMillis()))
    }

    private fun RoomResult.failOnError() {
        if (this is RoomResult.Failed) throw ServiceException(ErrorCode.BAD_REQUEST, message)
    }

    private companion object {
        const val ROOM_LIMIT = 50
        const val DELETE_USER = "DELETE_PLAYER"
        const val DELETE_ROOM = "DELETE_ROOM"
    }
}

@Serializable
data class AdminRoomView(
    val roomId: String,
    val code: String,
    val name: String,
    val description: String?,
    val selectedGame: String?,
    val members: Int,
    val leaderName: String,
    val adminName: String,
    val createdAt: Long,
)
