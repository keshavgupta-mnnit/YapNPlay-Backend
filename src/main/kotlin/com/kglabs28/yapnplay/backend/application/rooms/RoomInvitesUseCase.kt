package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.push.PushMessage
import com.kglabs28.yapnplay.backend.application.push.PushSender
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.application.social.FriendDirectory
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.FriendSummary
import com.kglabs28.yapnplay.backend.contract.InviteToRoomReply
import com.kglabs28.yapnplay.backend.contract.PushKeys
import com.kglabs28.yapnplay.backend.contract.RoomInviteSummary
import com.kglabs28.yapnplay.backend.contract.RoomInvites
import com.kglabs28.yapnplay.backend.contract.RoomSummary
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository
import com.kglabs28.yapnplay.backend.domain.rooms.RoomInvite
import com.kglabs28.yapnplay.backend.domain.rooms.RoomInviteRepository
import com.kglabs28.yapnplay.backend.domain.social.BlockRepository
import org.slf4j.LoggerFactory

/**
 * Group invites (contract 0.4.1): a member invites their own friends; the
 * invite is the approval, so accepting makes the user a member at once
 * (no Waiting List). A room holds at most MAX_MEMBERS_PER_ROOM members
 * and open invites together; invites past that are skipped.
 */
class RoomInvitesUseCase(
    private val rooms: PlayRoomRepository,
    private val invites: RoomInviteRepository,
    private val friends: FriendDirectory,
    private val blocks: BlockRepository,
    private val users: UserRepository,
    private val actors: PlayRoomActors,
    private val presence: RoomPresence,
    private val connections: ClientNotifier,
    private val push: PushSender,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val maxRoomsPerUser: Int,
) {
    /**
     * `POST /rooms/{roomId}/invites` (and CreateRoom's invitees): who got an
     * invite, who was skipped. [userIds] must be friends; a player found by
     * one of [userCodes] (typed in the room) needn't be. An unknown code is
     * skipped as the code itself.
     */
    suspend fun invite(inviterId: String, roomId: String, userIds: List<String>, userCodes: List<String> = emptyList()): InviteToRoomReply {
        val room = rooms.findById(roomId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NOT_FOUND)
        val members = rooms.members(roomId).map { it.userId }.toSet()
        if (inviterId !in members) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)
        val inviter = users.findById(inviterId) ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        val myFriends = friends.friendsOf(inviterId)
        val pending = invites.invitedTo(roomId).toMutableSet()
        val invited = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val byCode = mutableSetOf<String>()
        userCodes.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct().forEach { code ->
            val user = users.findByCode(code)
            if (user == null) skipped += code else byCode += user.id
        }
        (userIds + byCode).distinct().forEach { userId ->
            val ok = userId != inviterId &&
                (userId in myFriends || userId in byCode) &&
                userId !in members &&
                userId !in pending &&
                members.size + pending.size < BackendConstants.MAX_MEMBERS_PER_ROOM &&
                !blocks.isBlocked(inviterId, userId) &&
                !blocks.isBlocked(userId, inviterId) &&
                invites.insert(RoomInvite(ids.newId(), roomId, userId, inviterId, clock.nowMillis()))
            if (ok) {
                pending += userId
                invited += userId
            } else {
                skipped += userId
            }
        }
        if (invited.isNotEmpty()) notify(invited, room, inviter.displayName)
        return InviteToRoomReply(invited, skipped)
    }

    /** `GET /rooms/invites`: open invites, newest first (rooms the user already joined are dropped). */
    suspend fun list(userId: String): RoomInvites {
        val summaries = invites.forUser(userId).mapNotNull { invite ->
            val room = rooms.findById(invite.roomId)
            val inviter = users.findById(invite.invitedBy)
            when {
                room == null || inviter == null -> null.also { invites.delete(invite.id) }
                rooms.members(room.id).any { it.userId == userId } -> null.also { invites.delete(invite.id) }
                else -> RoomInviteSummary(
                    inviteId = invite.id,
                    roomId = room.id,
                    roomName = room.name,
                    roomIcon = room.icon,
                    memberCount = rooms.memberCount(room.id),
                    invitedBy = FriendSummary(
                        userId = inviter.id,
                        displayName = inviter.displayName,
                        online = connections.isConnected(inviter.id),
                        userCode = inviter.userCode.orEmpty(),
                        avatarId = inviter.avatarId,
                    ),
                    invitedAt = invite.createdAt,
                )
            }
        }
        return RoomInvites(summaries)
    }

    /** `POST /rooms/invites/{inviteId}/accept`: the user is a member straight away. */
    suspend fun accept(userId: String, inviteId: String): RoomSummary {
        val invite = invites.findById(inviteId)?.takeIf { it.userId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_INVITE)
        val room = rooms.findById(invite.roomId)
        if (room == null) {
            invites.delete(invite.id)
            throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_INVITE)
        }
        if (rooms.members(room.id).none { it.userId == userId }) {
            if (rooms.membershipCount(userId) >= maxRoomsPerUser) {
                throw ServiceException(ErrorCode.ROOM_LIMIT_REACHED, BackendStrings.ROOM_LIMIT_REACHED)
            }
            val user = users.findById(userId) ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
            val actor = actors.get(room.id) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_INVITE)
            val result = actor.ask(userId, RoomAction.AcceptInvite(user.displayName, user.avatarId))
            if (result is RoomResult.Failed) throw ServiceException(result.code, result.message)
        }
        invites.delete(invite.id)
        return RoomSummary(
            roomId = room.id,
            code = room.code,
            name = room.name,
            memberCount = rooms.memberCount(room.id),
            onlineCount = presence.onlineCount(room.id),
            isLeader = room.leaderId == userId,
            icon = room.icon,
        )
    }

    /** `POST /rooms/invites/{inviteId}/decline`: the inviter is not told. */
    suspend fun decline(userId: String, inviteId: String) {
        val invite = invites.findById(inviteId)?.takeIf { it.userId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_INVITE)
        invites.delete(invite.id)
    }

    private suspend fun notify(userIds: List<String>, room: PlayRoom, inviterName: String) {
        val message = PushMessage(
            mapOf(
                PushKeys.TYPE to PushKeys.TYPE_ROOM_INVITE,
                PushKeys.ROOM_ID to room.id,
                PushKeys.ROOM_NAME to room.name,
                PushKeys.TITLE to BackendStrings.roomInviteTitle(inviterName, room.name),
                PushKeys.BODY to BackendStrings.ROOM_INVITE_BODY,
            ),
        )
        // One push per invitee: each carries their own inviteId.
        userIds.forEach { userId ->
            val inviteId = invites.forUser(userId).firstOrNull { it.roomId == room.id }?.id ?: return@forEach
            runCatching { push.send(listOf(userId), message.copy(data = message.data + (PushKeys.INVITE_ID to inviteId))) }
                .onFailure { log.warn("Room invite push failed", it) }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(RoomInvitesUseCase::class.java)
    }
}
