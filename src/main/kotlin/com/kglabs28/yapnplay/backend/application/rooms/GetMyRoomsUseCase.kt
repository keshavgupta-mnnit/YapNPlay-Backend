package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.contract.MyRooms
import com.kglabs28.yapnplay.backend.contract.RoomSummary
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/** `GET /rooms/mine`: the user's rooms with member and live counts. */
class GetMyRoomsUseCase(
    private val rooms: PlayRoomRepository,
    private val presence: RoomPresence,
) {
    suspend operator fun invoke(userId: String): MyRooms = MyRooms(
        rooms.roomsOf(userId).map { room ->
            RoomSummary(
                roomId = room.id,
                code = room.code,
                name = room.name,
                memberCount = rooms.memberCount(room.id),
                onlineCount = presence.onlineCount(room.id),
                isLeader = room.leaderId == userId,
                icon = room.icon,
            )
        },
    )
}
