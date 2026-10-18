package com.kglabs28.yapnplay.backend.domain.rooms

/**
 * Turns a finished countdown into a match. Phase 6 ships a placeholder
 * that only hands out a match id; Phase 10 (match service) replaces it.
 */
fun interface MatchStarter {
    /** Starts the match and returns its id. */
    suspend fun start(roomId: String, plan: SeatPlan): String

    /** The unfinished match of [roomId], if one is running (one brought back after a restart, too). */
    fun runningMatchIn(roomId: String): String? = null
}
