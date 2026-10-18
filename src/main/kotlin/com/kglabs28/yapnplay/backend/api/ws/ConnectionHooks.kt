package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.application.match.MatchPresenceUseCase
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService

/**
 * What happens when a user's connection opens or finally closes (not when
 * a newer connection replaced it): a running block is re-sent; a dropped
 * player leaves the Quick Play queue and their seat starts its away grace.
 */
class ConnectionHooks(
    private val moderation: ModerationService,
    private val matchmaking: MatchmakingService,
    private val matchPresence: MatchPresenceUseCase,
) {
    suspend fun connected(userId: String) {
        moderation.sendActiveBlock(userId)
    }

    suspend fun disconnected(userId: String) {
        matchmaking.leave(userId)
        matchPresence.disconnected(userId)
    }
}
