package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomActor
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomDeps
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the live actor of a room, loading it from the database the first
 * time someone needs it. Rooms stay loaded until deleted (small numbers
 * for now; unloading idle rooms is a later optimisation).
 */
class PlayRoomActors(
    private val deps: PlayRoomDeps,
    private val scope: CoroutineScope,
) {
    private val actors = ConcurrentHashMap<String, PlayRoomActor>()
    private val loading = Mutex()

    /** The room's actor, or null when the room does not exist. */
    suspend fun get(roomId: String): PlayRoomActor? =
        actors[roomId] ?: loading.withLock { actors[roomId] ?: load(roomId)?.also { actors[roomId] = it } }

    /** The live actor without loading it (null when the room is not active). */
    fun peek(roomId: String): PlayRoomActor? = actors[roomId]

    fun shutdown() {
        actors.values.forEach { it.stop() }
        actors.clear()
    }

    private suspend fun load(roomId: String): PlayRoomActor? {
        val room = deps.repository.findById(roomId) ?: return null
        val state = PlayRoomState(
            room = room,
            memberships = deps.repository.members(roomId),
            houseRules = deps.games.defaultHouseRules(room.selectedGame),
            waiting = deps.joinRequests.listFor(roomId).toMutableList(),
        )
        // A match brought back after a restart still holds the room.
        state.matchInProgress = deps.matchStarter.runningMatchIn(roomId)
        return PlayRoomActor(state, deps, scope, onDeleted = { actors.remove(it) })
    }
}
