package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinRoomResult
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator

/**
 * `JoinRoom(code)`: re-enters a room the user belongs to (moving them out
 * of any other), or asks the admin to let them in (PENDING_APPROVAL).
 */
class JoinPlayRoomUseCase(
    private val rooms: PlayRoomRepository,
    private val users: UserRepository,
    private val codes: RoomCodeGenerator,
    private val actors: PlayRoomActors,
    private val exitCurrentRoom: ExitCurrentRoomUseCase,
) {
    suspend operator fun invoke(userId: String, rawCode: String): JoinRoomResult {
        val code = rawCode.trim()
        if (!codes.isValid(code)) throw ServiceException(ErrorCode.INVALID_CODE, BackendStrings.INVALID_CODE)
        val room = rooms.findByCode(code) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        val user = users.findById(userId)
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        // Only a member moves rooms; anyone else just asks to join (and stays where they are).
        if (rooms.members(room.id).any { it.userId == userId }) exitCurrentRoom(userId, force = false, exceptRoomId = room.id)
        val actor = actors.get(room.id) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        return (actor.ask(userId, RoomAction.Join(user.displayName)).orThrow() as RoomResult.Joined).result
    }
}
