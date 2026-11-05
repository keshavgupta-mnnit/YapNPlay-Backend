package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/**
 * `SaveRoom(name?)`: the leader of a Quick Play party keeps the people in it
 * as a Game Room. With a [name]: a new room (the leader its leader and admin)
 * with everyone in the party added at once, no invites. Without: the people
 * who joined the party since are added to that room (Update). The party
 * itself carries on; its snapshot says which room it was saved as and how
 * many people here aren't in it yet. Someone already in the most rooms they
 * can have is left out.
 */
class SaveTempRoomUseCase(
    private val rooms: PlayRoomRepository,
    private val presence: RoomPresence,
    private val actors: PlayRoomActors,
    private val inserter: PlayRoomInserter,
    private val maxRoomsPerUser: Int,
) {
    suspend operator fun invoke(userId: String, name: String?) {
        val partyId = presence.roomOf(userId) ?: throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_A_ROOM)
        val party = actors.get(partyId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        val info = (party.ask(userId, RoomAction.Snapshot).orThrow() as RoomResult.Snapshot).snapshot.room
        if (!info.temporary) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.SAVE_ONLY_PARTY)
        if (info.leaderId != userId) throw ServiceException(ErrorCode.NOT_LEADER, BackendStrings.NOT_LEADER)
        val saved = info.savedRoomId?.let { actors.get(it) } ?: run {
            if (info.members.size < MIN_PEOPLE) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.SAVE_NEEDS_PEOPLE)
            if (rooms.membershipCount(userId) >= maxRoomsPerUser) {
                throw ServiceException(ErrorCode.ROOM_LIMIT_REACHED, BackendStrings.ROOM_LIMIT_REACHED)
            }
            val created = inserter.insert(userId, roomName(name), BackendConstants.DEFAULT_ROOM_ICON, temporary = false)
            checkNotNull(actors.get(created.id))
        }
        // Everyone here becomes a member at once (as if they had accepted an invite).
        info.members.filter { it.userId != userId }.forEach { member ->
            saved.ask(member.userId, RoomAction.AcceptInvite(member.displayName, member.avatarId))
        }
        val inSaved = rooms.members(saved.roomId).map { it.userId }.toSet()
        party.ask(userId, RoomAction.SetSavedRoom(saved.roomId, inSaved)).orThrow()
    }

    private fun roomName(requested: String?): String {
        val trimmed = requested?.trim().orEmpty()
        val valid = trimmed.isNotEmpty() && trimmed.length <= BackendConstants.ROOM_NAME_MAX_LENGTH && trimmed.none { it.isISOControl() }
        if (!valid) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.INVALID_ROOM_NAME)
        return trimmed
    }

    private companion object {
        /** A Game Room of one is no group: the party needs someone else in it. */
        const val MIN_PEOPLE = 2
    }
}
