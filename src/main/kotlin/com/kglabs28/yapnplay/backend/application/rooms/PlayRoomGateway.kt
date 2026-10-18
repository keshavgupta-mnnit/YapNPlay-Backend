package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.match.RoomGateway
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction

/** Lets matches read their room (leader, who is present) and report the end. */
class PlayRoomGateway(
    actorsProvider: Lazy<PlayRoomActors>,
    private val presence: RoomPresence,
) : RoomGateway {
    private val actors by actorsProvider

    override fun leaderOf(roomId: String): String? = actors.peek(roomId)?.leaderId

    override fun isPresent(userId: String, roomId: String): Boolean = presence.roomOf(userId) == roomId

    override suspend fun matchEnded(roomId: String, matchId: String) {
        actors.get(roomId)?.ask(SERVER_USER, RoomAction.MatchEnded(matchId))
    }

    private companion object {
        /** Requests the server makes itself, not on behalf of a member. */
        const val SERVER_USER = "server"
    }
}
