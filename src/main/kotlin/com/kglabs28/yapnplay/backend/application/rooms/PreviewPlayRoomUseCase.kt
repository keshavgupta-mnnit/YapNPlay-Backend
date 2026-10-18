package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RoomPreview
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator

/** `GET /rooms/preview/{code}`: what the "Join this room?" dialog shows. */
class PreviewPlayRoomUseCase(
    private val rooms: PlayRoomRepository,
    private val codes: RoomCodeGenerator,
    private val presence: RoomPresence,
) {
    suspend operator fun invoke(userId: String, rawCode: String): RoomPreview {
        val code = rawCode.trim()
        if (!codes.isValid(code)) throw ServiceException(ErrorCode.INVALID_CODE, BackendStrings.INVALID_CODE)
        val room = rooms.findByCode(code) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        val members = rooms.members(room.id)
        return RoomPreview(
            roomId = room.id,
            code = room.code,
            name = room.name,
            description = room.description,
            memberCount = members.size,
            onlineCount = presence.onlineCount(room.id),
            isMember = members.any { it.userId == userId },
        )
    }
}
