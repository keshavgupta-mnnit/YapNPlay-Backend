package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RoomPendingInvites
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomInviteRepository

/** `GET /rooms/{roomId}/invites`: who already has an invite to the room, not yet answered (members only). */
class GetRoomPendingInvitesUseCase(
    private val rooms: PlayRoomRepository,
    private val invites: RoomInviteRepository,
) {
    suspend operator fun invoke(userId: String, roomId: String): RoomPendingInvites {
        if (rooms.members(roomId).none { it.userId == userId }) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)
        return RoomPendingInvites(invites.invitedTo(roomId).sorted())
    }
}
