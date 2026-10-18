package com.kglabs28.yapnplay.backend.application.push

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.PushKeys
import com.kglabs28.yapnplay.backend.domain.common.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Room pushes (contract "Room activity push"): when a member comes online,
 * the members who are not present get "<name> entered <room>", at most once
 * per user and room every few minutes. Also "you're in" after approval.
 */
class RoomActivityNotifier(
    private val sender: PushSender,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val lastSent = ConcurrentHashMap<String, Long>()

    fun memberCameOnline(roomId: String, roomName: String, arrivalName: String, onlineCount: Int, absentMembers: Collection<String>) {
        val now = clock.nowMillis()
        val due = absentMembers.filter { user ->
            val key = user + KEY_SEPARATOR + roomId
            val last = lastSent[key]
            (last == null || now - last >= BackendConstants.ROOM_PUSH_THROTTLE_MS).also { if (it) lastSent[key] = now }
        }
        if (due.isEmpty()) return
        val message = roomMessage(roomId, roomName, BackendStrings.pushEntered(arrivalName, roomName), BackendStrings.pushPlayersIn(onlineCount, roomName))
        scope.launch { sender.send(due, message) }
    }

    fun joinApproved(userId: String, roomId: String, roomName: String) {
        val message = roomMessage(roomId, roomName, BackendStrings.pushApprovedTitle(roomName), BackendStrings.PUSH_APPROVED_BODY)
        scope.launch { sender.send(listOf(userId), message) }
    }

    private fun roomMessage(roomId: String, roomName: String, title: String, body: String) = PushMessage(
        mapOf(
            PushKeys.TYPE to PushKeys.TYPE_ROOM_ACTIVITY,
            PushKeys.ROOM_ID to roomId,
            PushKeys.ROOM_NAME to roomName,
            PushKeys.TITLE to title,
            PushKeys.BODY to body,
        ),
    )

    private companion object {
        const val KEY_SEPARATOR = "|"
    }
}
