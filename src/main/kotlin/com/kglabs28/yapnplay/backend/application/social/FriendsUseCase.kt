package com.kglabs28.yapnplay.backend.application.social

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.rooms.PlayRoomActors
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.BlockedUserSummary
import com.kglabs28.yapnplay.backend.contract.BlockedUsers
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.FriendRequestOutcomeCode
import com.kglabs28.yapnplay.backend.contract.FriendRequestSummary
import com.kglabs28.yapnplay.backend.contract.FriendRequests
import com.kglabs28.yapnplay.backend.contract.FriendSummary
import com.kglabs28.yapnplay.backend.contract.Friends
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserCodeGenerator
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.social.BlockRepository
import com.kglabs28.yapnplay.backend.domain.social.FriendRequestRecord
import com.kglabs28.yapnplay.backend.domain.social.FriendRequestRepository

/**
 * Friends (contract 0.3.1): added only by the 6-character ID, through a
 * request the other person accepts; friendship is mutual; blocking ends
 * it and stops their requests. Rooms refresh their friend flags at once.
 */
class FriendsUseCase(
    private val friends: FriendDirectory,
    private val requests: FriendRequestRepository,
    private val blocks: BlockRepository,
    private val users: UserRepository,
    private val codes: UserCodeGenerator,
    private val connections: ClientNotifier,
    private val presence: RoomPresence,
    private val rooms: PlayRoomActors,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val pushes: FriendPushes,
) {
    suspend fun list(userId: String): Friends = Friends(
        friends.friendsOf(userId).mapNotNull { users.findById(it)?.toSummary() }
            .sortedWith(compareBy({ !it.online }, { it.displayName })),
    )

    suspend fun incoming(userId: String): FriendRequests = FriendRequests(
        requests.incoming(userId).mapNotNull { request ->
            users.findById(request.fromId)?.let { FriendRequestSummary(request.id, it.toSummary()) }
        },
    )

    /** Asks by friend ID ([rawCode]) or, from a room member card, by [targetUserId]. */
    suspend fun send(userId: String, rawCode: String, targetUserId: String? = null): FriendRequestOutcomeCode {
        val target = (targetUserId?.let { users.findById(it) } ?: users.findByCode(codes.normalize(rawCode)))
            ?: return FriendRequestOutcomeCode.NOT_FOUND
        return when {
            target.id == userId -> FriendRequestOutcomeCode.SELF
            // Someone who blocked the user does not exist for them.
            blocks.isBlocked(target.id, userId) -> FriendRequestOutcomeCode.NOT_FOUND
            blocks.isBlocked(userId, target.id) -> FriendRequestOutcomeCode.BLOCKED
            target.id in friends.friendsOf(userId) -> FriendRequestOutcomeCode.ALREADY_FRIENDS
            requests.between(userId, target.id) != null -> FriendRequestOutcomeCode.ALREADY_REQUESTED
            else -> requests.between(target.id, userId)?.let { theirs ->
                // They had asked first: this answers it, and they hear it was accepted.
                requests.delete(theirs.id)
                befriend(userId, target.id)
                pushes.accepted(target.id, userId, nameOf(userId))
                FriendRequestOutcomeCode.ACCEPTED
            } ?: run {
                requests.create(FriendRequestRecord(ids.newId(), userId, target.id, clock.nowMillis()))
                pushes.requested(target.id, userId, nameOf(userId))
                FriendRequestOutcomeCode.SENT
            }
        }
    }

    suspend fun accept(userId: String, requestId: String) {
        val request = ownRequest(userId, requestId)
        requests.delete(request.id)
        befriend(request.fromId, userId)
        pushes.accepted(request.fromId, userId, nameOf(userId))
    }

    private suspend fun nameOf(userId: String): String = users.findById(userId)?.displayName.orEmpty()

    /** The sender is not told. */
    suspend fun decline(userId: String, requestId: String) {
        requests.delete(ownRequest(userId, requestId).id)
    }

    suspend fun remove(userId: String, friendId: String) {
        friends.unfriend(userId, friendId)
        refreshRooms(userId, friendId)
    }

    suspend fun block(userId: String, targetId: String) {
        if (targetId == userId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CANNOT_FRIEND_SELF)
        users.findById(targetId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NOT_FOUND)
        blocks.block(userId, targetId, clock.nowMillis())
        requests.deleteBetween(userId, targetId)
        friends.unfriend(userId, targetId)
        refreshRooms(userId, targetId)
    }

    /** `GET /blocks`: newest first. */
    suspend fun blocked(userId: String): BlockedUsers = BlockedUsers(
        blocks.blockedBy(userId).mapNotNull { record ->
            users.findById(record.blockedId)?.let {
                BlockedUserSummary(it.id, it.displayName, it.avatarId, record.blockedAt)
            }
        },
    )

    /** `DELETE /blocks/{userId}`: no friendship comes back. */
    suspend fun unblock(userId: String, targetId: String) {
        blocks.unblock(userId, targetId)
    }

    private suspend fun ownRequest(userId: String, requestId: String): FriendRequestRecord =
        requests.find(requestId)?.takeIf { it.toId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_FRIEND_REQUEST)

    private suspend fun befriend(a: String, b: String) {
        friends.befriend(a, b)
        refreshRooms(a, b)
    }

    private suspend fun refreshRooms(vararg userIds: String) {
        userIds.forEach { user -> presence.roomOf(user)?.let { rooms.get(it)?.ask(user, RoomAction.Resend) } }
    }

    private fun User.toSummary() = FriendSummary(
        userId = id,
        displayName = displayName,
        online = connections.isConnected(id),
        userCode = userCode.orEmpty(),
        avatarId = avatarId,
    )
}
