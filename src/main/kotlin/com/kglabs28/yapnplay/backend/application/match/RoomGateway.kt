package com.kglabs28.yapnplay.backend.application.match

/** What a match needs to know about its room, without depending on the room actors. */
interface RoomGateway {
    /** The room's current leader, or null. */
    fun leaderOf(roomId: String): String?

    /** True while [userId] is present in [roomId]. */
    fun isPresent(userId: String, roomId: String): Boolean

    /** The room's match ended: clear "match in progress" and tell the lobby. */
    suspend fun matchEnded(roomId: String, matchId: String)
}
