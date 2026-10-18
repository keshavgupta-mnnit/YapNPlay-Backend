package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinRoomResult
import com.kglabs28.yapnplay.backend.contract.JoinStatus
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.rooms.LiveMember
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequest
import com.kglabs28.yapnplay.backend.contract.JoinApproved
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType

/**
 * Joining, being present, leaving and removal, with the leader and admin
 * hand-overs they cause.
 */
class MembershipHandler(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val guards: RoomGuards,
    private val countdown: CountdownHandler,
) {
    /**
     * A member enters the room. Anyone else asks to join: the request goes
     * on the admin's Waiting List and the answer is PENDING_APPROVAL.
     */
    suspend fun join(userId: String, displayName: String, avatarId: String = ""): JoinRoomResult {
        if (userId !in state.members) {
            checkRoomFor(userId)
            if (state.waiting.none { it.userId == userId }) {
                val now = deps.clock.nowMillis()
                deps.joinRequests.add(state.room.id, userId, now)
                state.waiting += JoinRequest(state.room.id, userId, displayName, now, avatarId)
            }
            return JoinRoomResult(JoinStatus.PENDING_APPROVAL, state.room.name)
        }
        enter(userId)
        return JoinRoomResult(JoinStatus.JOINED, state.room.name, state.summary(userId))
    }

    /** The admin lets someone from the Waiting List in; they are told by WebSocket and push. */
    suspend fun approve(adminId: String, userId: String) {
        guards.requireAdmin(adminId)
        val request = state.waiting.firstOrNull { it.userId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_JOIN_REQUEST)
        checkRoomFor(userId)
        val now = deps.clock.nowMillis()
        deps.repository.addMember(state.room.id, userId, now)
        deps.joinRequests.remove(state.room.id, userId)
        state.waiting.remove(request)
        state.members[userId] = LiveMember(userId, request.displayName, now, avatarId = request.avatarId)
        val approved = deps.codec.push(Channel.ROOM, MessageType.JOIN_APPROVED, JoinApproved.serializer(), JoinApproved(state.room.id, state.room.name))
        deps.notifier.send(userId, approved)
        deps.activity.joinApproved(userId, state.room.id, state.room.name)
    }

    suspend fun decline(adminId: String, userId: String) {
        guards.requireAdmin(adminId)
        val request = state.waiting.firstOrNull { it.userId == userId }
            ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_JOIN_REQUEST)
        deps.joinRequests.remove(state.room.id, userId)
        state.waiting.remove(request)
    }

    private suspend fun checkRoomFor(userId: String) {
        if (state.members.size >= BackendConstants.MAX_MEMBERS_PER_ROOM) {
            throw ServiceException(ErrorCode.ROOM_FULL, BackendStrings.ROOM_FULL)
        }
        if (deps.repository.membershipCount(userId) >= deps.maxRoomsPerUser) {
            throw ServiceException(ErrorCode.ROOM_LIMIT_REACHED, BackendStrings.ROOM_LIMIT_REACHED)
        }
    }

    suspend fun enter(userId: String) {
        val member = state.member(userId)
        if (!member.online) {
            state.update(userId) { copy(online = true, onlineSince = deps.clock.nowMillis()) }
            deps.presence.enter(userId, state.room.id)
            val absent = state.members.values.filter { !it.online }.map { it.userId }
            deps.activity.memberCameOnline(state.room.id, state.room.name, member.displayName, state.onlineIds().size, absent)
        }
        val leader = deps.rolePolicy.leaderAfterArrival(state.members, state.room.leaderId, userId)
        if (leader != state.room.leaderId) setRoles(leader, state.room.adminId)
    }

    suspend fun exit(userId: String, force: Boolean) {
        val member = state.member(userId)
        if (!force && countdown.isSeatedInCountdown(userId)) {
            throw ServiceException(ErrorCode.ALREADY_IN_ROOM, BackendStrings.BUSY_IN_COUNTDOWN)
        }
        if (!member.online) return
        goOffline(userId)
        if (state.room.leaderId == userId) {
            deps.rolePolicy.nextLeader(state.members.values, userId)?.let { setRoles(it, state.room.adminId) }
        }
        countdown.revalidate()
    }

    /** Returns true when the room was deleted because nobody is left. */
    suspend fun leave(userId: String): Boolean {
        state.member(userId)
        return removeMembership(userId)
    }

    /** Admin removes [targetId]; they still get one last snapshot without themselves. */
    suspend fun remove(adminId: String, targetId: String): Boolean {
        guards.requireAdmin(adminId)
        if (adminId == targetId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.CANNOT_REMOVE_SELF)
        guards.requireTarget(targetId)
        if (state.members.getValue(targetId).online) state.extraRecipients += targetId
        return removeMembership(targetId)
    }

    fun updateProfile(userId: String, displayName: String?, avatarId: String?) {
        state.update(userId) { copy(displayName = displayName ?: this.displayName, avatarId = avatarId ?: this.avatarId) }
    }

    private suspend fun removeMembership(userId: String): Boolean {
        val wasLeader = state.room.leaderId == userId
        val wasAdmin = state.room.adminId == userId
        if (state.member(userId).online) goOffline(userId)
        state.members.remove(userId)
        deps.repository.removeMember(state.room.id, userId)
        if (state.members.isEmpty()) {
            countdown.cancel()
            deps.repository.delete(state.room.id)
            return true
        }
        val roles = deps.rolePolicy
        val leader = if (wasLeader) {
            roles.nextLeader(state.members.values, userId) ?: roles.nextAdmin(state.members.values, userId)
        } else {
            state.room.leaderId
        }
        val admin = if (wasAdmin) roles.nextAdmin(state.members.values, userId) else state.room.adminId
        if (wasLeader || wasAdmin) setRoles(checkNotNull(leader), checkNotNull(admin))
        countdown.revalidate()
        return false
    }

    private fun goOffline(userId: String) {
        state.update(userId) {
            copy(online = false, onlineSince = null, ready = false, readyAt = null, watching = false)
        }
        deps.presence.exit(userId, state.room.id)
    }

    private suspend fun setRoles(leaderId: String, adminId: String) {
        state.room = state.room.copy(leaderId = leaderId, adminId = adminId)
        deps.repository.updateRoles(state.room.id, leaderId, adminId)
    }
}
