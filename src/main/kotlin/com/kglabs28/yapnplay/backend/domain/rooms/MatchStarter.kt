package com.kglabs28.yapnplay.backend.domain.rooms

/**
 * Turns a finished countdown into a match. Phase 6 ships a placeholder
 * that only hands out a match id; Phase 10 (match service) replaces it.
 */
fun interface MatchStarter {
    /** Starts the match and returns its id. */
    suspend fun start(roomId: String, plan: SeatPlan): String
}
