package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomCodeGenerator

/**
 * `CreateRoom(name?)`: the creator becomes member, leader and admin, and
 * is moved into the new room. Answers with the first snapshot.
 */
class CreatePlayRoomUseCase(
    private val rooms: PlayRoomRepository,
    private val users: UserRepository,
    private val codes: RoomCodeGenerator,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val actors: PlayRoomActors,
    private val exitCurrentRoom: ExitCurrentRoomUseCase,
    private val maxRoomsPerUser: Int,
) {
    suspend operator fun invoke(userId: String, requestedName: String?): RoomSnapshot {
        val user = users.findById(userId)
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        if (rooms.membershipCount(userId) >= maxRoomsPerUser) {
            throw ServiceException(ErrorCode.ROOM_LIMIT_REACHED, BackendStrings.ROOM_LIMIT_REACHED)
        }
        val name = roomName(requestedName, user.displayName)
        exitCurrentRoom(userId, force = false)
        val room = insertWithFreeCode(userId, name)
        val actor = checkNotNull(actors.get(room.id))
        actor.ask(userId, RoomAction.Join(user.displayName, user.avatarId)).orThrow()
        return (actor.ask(userId, RoomAction.Snapshot).orThrow() as RoomResult.Snapshot).snapshot
    }

    private fun roomName(requested: String?, ownerName: String): String {
        val trimmed = requested?.trim().orEmpty()
        if (trimmed.isEmpty()) return BackendStrings.defaultRoomName(ownerName).take(BackendConstants.ROOM_NAME_MAX_LENGTH)
        val valid = trimmed.length <= BackendConstants.ROOM_NAME_MAX_LENGTH && trimmed.none { it.isISOControl() }
        if (!valid) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_ROOM_NAME)
        return trimmed
    }

    /** Random codes collide rarely; the unique column catches it and we try another. */
    private suspend fun insertWithFreeCode(userId: String, name: String): PlayRoom {
        repeat(BackendConstants.ROOM_CODE_MAX_ATTEMPTS) {
            val now = clock.nowMillis()
            val room = PlayRoom(
                id = ids.newId(),
                code = codes.newCode(),
                name = name,
                description = null,
                icon = BackendConstants.DEFAULT_ROOM_ICON,
                createdBy = userId,
                leaderId = userId,
                adminId = userId,
                selectedGame = null,
                botFill = false,
                createdAt = now,
            )
            if (rooms.insertWithCreator(room, now)) return room
        }
        throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CODE_SPACE_EXHAUSTED)
    }
}
