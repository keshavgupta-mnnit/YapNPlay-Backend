package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.LeaderRequestInfo

/**
 * Asking to lead: while the room's admin is away, a member who finds the leader not answering asks
 * to take over. The leader is asked and may refuse within [BackendConstants.LEADER_REQUEST_MS];
 * unrefused, the asker leads ([LeaderChange]: both start not ready). [schedule] wakes the room when
 * the time is up.
 */
class LeaderRequestHandler(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val guards: RoomGuards,
    private val leaderChange: LeaderChange,
    private val schedule: (endsAt: Long, delayMillis: Long) -> Unit,
) {
    fun request(userId: String) {
        val member = state.member(userId)
        if (state.room.leaderId == userId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.ALREADY_LEADER)
        if (adminPresent()) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.ADMIN_LEADS)
        if (state.leaderRequest != null) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.LEADER_REQUEST_BUSY)
        val endsAt = deps.clock.nowMillis() + BackendConstants.LEADER_REQUEST_MS
        state.leaderRequest = LeaderRequestInfo(userId, member.displayName, endsAt)
        schedule(endsAt, BackendConstants.LEADER_REQUEST_MS)
    }

    fun deny(userId: String) {
        guards.requireLeader(userId)
        if (state.leaderRequest == null) throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_LEADER_REQUEST)
        state.leaderRequest = null
    }

    /** The time is up: unrefused (and still standing), the asker leads. */
    suspend fun elapsed(endsAt: Long) {
        val request = state.leaderRequest ?: return
        if (request.endsAt != endsAt) return
        state.leaderRequest = null
        if (stillStands(request)) leaderChange.to(request.userId, request.userId)
    }

    /** After any change: a request that no longer makes sense (asker gone, admin back, asker leads) is dropped. */
    fun dropIfStale() {
        val request = state.leaderRequest ?: return
        if (!stillStands(request)) state.leaderRequest = null
    }

    private fun stillStands(request: LeaderRequestInfo): Boolean =
        state.members[request.userId]?.online == true && state.room.leaderId != request.userId && !adminPresent()

    private fun adminPresent(): Boolean = state.members[state.room.adminId]?.online == true
}
