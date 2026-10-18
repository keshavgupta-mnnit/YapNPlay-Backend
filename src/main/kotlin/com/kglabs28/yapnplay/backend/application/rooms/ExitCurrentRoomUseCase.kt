package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction

/**
 * Stops the user being present wherever they are. [force] false refuses
 * while they are about to play in that room's countdown (room switch);
 * a dropped connection uses force true.
 */
class ExitCurrentRoomUseCase(
    private val presence: RoomPresence,
    private val actors: PlayRoomActors,
) {
    suspend operator fun invoke(userId: String, force: Boolean, exceptRoomId: String? = null) {
        val roomId = presence.roomOf(userId) ?: return
        if (roomId == exceptRoomId) return
        actors.get(roomId)?.ask(userId, RoomAction.Exit(force))?.orThrow()
    }
}
