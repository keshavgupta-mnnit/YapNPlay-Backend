package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.domain.rooms.RoomInviteRepository
import com.kglabs28.yapnplay.backend.contract.CreateRoom
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
 * `CreateRoom(name, icon?, inviteUserIds)`: the creator becomes member,
 * leader and admin, is moved into the new room, and the friends among
 * [CreateRoom.inviteUserIds] get an invite. A name and at least one
 * invitee are required; a group the creator already has with exactly these
 * people (members and open invites) is refused with GROUP_EXISTS, its name
 * in the message. Answers with the first snapshot.
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
    private val invites: RoomInvitesUseCase,
    private val openInvites: RoomInviteRepository,
) {
    suspend operator fun invoke(userId: String, request: CreateRoom): RoomSnapshot {
        val user = users.findById(userId)
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        if (request.name.isNullOrBlank()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.GROUP_NAME_REQUIRED)
        val invitees = request.inviteUserIds.filter { it.isNotBlank() && it != userId }.distinct()
        if (invitees.isEmpty()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.GROUP_NEEDS_INVITEES)
        if (rooms.membershipCount(userId) >= maxRoomsPerUser) {
            throw ServiceException(ErrorCode.ROOM_LIMIT_REACHED, BackendStrings.ROOM_LIMIT_REACHED)
        }
        sameGroup(userId, invitees.toSet() + userId)?.let { throw ServiceException(ErrorCode.GROUP_EXISTS, it.name) }
        val name = roomName(request.name, user.displayName)
        exitCurrentRoom(userId, force = false)
        val room = insertWithFreeCode(userId, name, iconOf(request.icon))
        val actor = checkNotNull(actors.get(room.id))
        actor.ask(userId, RoomAction.Join(user.displayName, user.avatarId)).orThrow()
        // Friends get an invite; anyone else is skipped silently.
        invites.invite(userId, room.id, invitees)
        return (actor.ask(userId, RoomAction.Snapshot).orThrow() as RoomResult.Snapshot).snapshot
    }

    /** A group [userId] belongs to whose members and open invites are exactly [people]. */
    private suspend fun sameGroup(userId: String, people: Set<String>): PlayRoom? =
        rooms.roomsOf(userId).firstOrNull { room ->
            rooms.members(room.id).map { it.userId }.toSet() + openInvites.invitedTo(room.id) == people
        }

    private fun iconOf(requested: String?): String =
        requested?.trim()?.lowercase()?.takeIf { it in BackendConstants.ROOM_ICONS } ?: BackendConstants.DEFAULT_ROOM_ICON

    private fun roomName(requested: String?, ownerName: String): String {
        val trimmed = requested?.trim().orEmpty()
        if (trimmed.isEmpty()) return BackendStrings.defaultRoomName(ownerName).take(BackendConstants.ROOM_NAME_MAX_LENGTH)
        val valid = trimmed.length <= BackendConstants.ROOM_NAME_MAX_LENGTH && trimmed.none { it.isISOControl() }
        if (!valid) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_ROOM_NAME)
        return trimmed
    }

    /** Random codes collide rarely; the unique column catches it and we try another. */
    private suspend fun insertWithFreeCode(userId: String, name: String, icon: String): PlayRoom {
        repeat(BackendConstants.ROOM_CODE_MAX_ATTEMPTS) {
            val now = clock.nowMillis()
            val room = PlayRoom(
                id = ids.newId(),
                code = codes.newCode(),
                name = name,
                description = null,
                icon = icon,
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
