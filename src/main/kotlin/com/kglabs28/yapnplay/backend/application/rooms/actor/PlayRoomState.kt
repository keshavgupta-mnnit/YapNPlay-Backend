package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Presence
import com.kglabs28.yapnplay.backend.contract.JoinRequestInfo
import com.kglabs28.yapnplay.backend.domain.rooms.JoinRequest
import com.kglabs28.yapnplay.backend.contract.RoomInfo
import com.kglabs28.yapnplay.backend.contract.RoomMember
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.contract.RoomSummary
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.rooms.LiveMember
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomMembership

/**
 * One room's state, owned by its actor (only the actor's single worker
 * touches it, so no locks are needed). Builds the wire snapshot.
 */
class PlayRoomState(
    var room: PlayRoom,
    memberships: List<PlayRoomMembership>,
    var houseRules: Map<String, Boolean>,
    /** The admin's Waiting List, oldest first. */
    val waiting: MutableList<JoinRequest> = mutableListOf(),
) {
    /** Members in joining order. */
    val members = LinkedHashMap<String, LiveMember>().apply {
        memberships.sortedBy { it.joinedAt }.forEach { put(it.userId, LiveMember(it.userId, it.displayName, it.joinedAt, avatarId = it.avatarId)) }
    }
    var version = 0L
    var countdownDeadline: Long? = null
    var matchInProgress: String? = null
    /** The leagues on offer (coins), and the most every member in the lobby can pay (refreshed with each snapshot). */
    var leagueFees: List<Int> = emptyList()
    var affordableEntry: Int = Int.MAX_VALUE

    /** Users who must get the next snapshot although they are no longer members (removed). */
    val extraRecipients = mutableSetOf<String>()

    fun member(userId: String): LiveMember =
        members[userId] ?: throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)

    fun update(userId: String, transform: LiveMember.() -> LiveMember) {
        members[userId] = member(userId).transform()
    }

    fun updateAll(transform: LiveMember.() -> LiveMember) {
        members.replaceAll { _, member -> member.transform() }
    }

    fun onlineIds(): List<String> = members.values.filter { it.online }.map { it.userId }

    /** The snapshot [receiver] sees: their own friend flags, and the Waiting List only for the admin. */
    fun snapshot(now: Long, receiver: String? = null, friends: Set<String> = emptySet()): RoomSnapshot = RoomSnapshot(
        version = version,
        serverTime = now,
        room = RoomInfo(
            id = room.id,
            code = room.code,
            name = room.name,
            leaderId = room.leaderId,
            adminId = room.adminId,
            description = room.description,
            icon = room.icon,
            members = members.values.map { it.toWire(it.userId in friends) },
            selectedGame = room.selectedGame,
            botFill = room.botFill,
            houseRules = houseRules,
            entryCoins = room.entryCoins,
            leagueFees = leagueFees,
            affordableEntry = affordableEntry,
            countdownDeadline = countdownDeadline,
            matchInProgress = matchInProgress,
            waitingList = if (receiver != null && receiver == room.adminId) {
                waiting.map { JoinRequestInfo(it.userId, it.displayName, it.requestedAt) }
            } else {
                emptyList()
            },
        ),
    )

    fun summary(userId: String): RoomSummary = RoomSummary(
        roomId = room.id,
        code = room.code,
        name = room.name,
        memberCount = members.size,
        onlineCount = members.values.count { it.online },
        isLeader = room.leaderId == userId,
        icon = room.icon,
    )

    private fun LiveMember.toWire(isFriend: Boolean) = RoomMember(
        userId = userId,
        displayName = displayName,
        presence = if (online) Presence.ONLINE else Presence.OFFLINE,
        ready = ready,
        watching = watching,
        micOn = micOn,
        speakerOn = speakerOn,
        isFriend = isFriend,
        onlineSince = onlineSince,
        avatarId = avatarId,
    )
}
