package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.rooms.MatchStarter
import com.kglabs28.yapnplay.backend.domain.rooms.SeatPlan

/**
 * Phase 6 stand-in: hands out a match id so the room flow can be tested
 * end to end. Phase 10 replaces it with the real match service.
 */
class PlaceholderMatchStarter(private val ids: IdGenerator) : MatchStarter {
    override suspend fun start(roomId: String, plan: SeatPlan): String = ids.newId()
}
