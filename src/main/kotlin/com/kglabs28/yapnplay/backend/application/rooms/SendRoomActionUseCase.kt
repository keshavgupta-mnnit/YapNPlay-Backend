package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.config.BackendStrings

/**
 * Lobby messages carry no room id: they go to the room the user is
 * present in. Not present anywhere means `NOT_MEMBER`.
 */
class SendRoomActionUseCase(
    private val presence: RoomPresence,
    private val actors: PlayRoomActors,
) {
    suspend operator fun invoke(userId: String, action: RoomAction): RoomResult {
        val roomId = presence.roomOf(userId)
            ?: throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_A_ROOM)
        val actor = actors.get(roomId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        return actor.ask(userId, action).orThrow()
    }
}
