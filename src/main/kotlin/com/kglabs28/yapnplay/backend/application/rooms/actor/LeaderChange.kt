package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.CountdownStopReason

/**
 * Hands the leader role to a member: the one stepping down and the new leader both start not ready
 * (the leader counted as ready), a temporary room's admin follows the leader (it has one leader
 * only), any request to lead is answered, and a running countdown is checked again. Shared by every
 * way the leader changes hands.
 */
class LeaderChange(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val countdown: CountdownHandler,
) {
    suspend fun to(newLeader: String, byUserId: String) {
        val previous = state.room.leaderId
        if (previous == newLeader) return
        val admin = if (state.room.temporary) newLeader else state.room.adminId
        state.room = state.room.copy(leaderId = newLeader, adminId = admin)
        listOf(previous, newLeader).filter { it in state.members }.forEach { state.update(it) { copy(ready = false, readyAt = null) } }
        state.leaderRequest = null
        deps.repository.updateRoles(state.room.id, newLeader, admin)
        countdown.revalidate(CountdownStopReason.SETTINGS_CHANGED, byUserId)
    }
}
