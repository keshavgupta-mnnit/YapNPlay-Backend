package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.application.identity.OnlineTimeTracker
import com.kglabs28.yapnplay.backend.application.match.MatchPresenceUseCase
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService
import com.kglabs28.yapnplay.backend.application.voice.VoiceHub

/**
 * What happens when a user's connection opens or finally closes (not when
 * a newer connection replaced it): a running block is re-sent; a dropped
 * player leaves their voice room and the Quick Play queue, their seat starts its away grace;
 * their time in the app is counted.
 */
class ConnectionHooks(
    private val moderation: ModerationService,
    private val matchmaking: MatchmakingService,
    private val matchPresence: MatchPresenceUseCase,
    private val onlineTime: OnlineTimeTracker,
    private val voice: VoiceHub,
) {
    suspend fun connected(userId: String) {
        onlineTime.connected(userId)
        moderation.sendActiveBlock(userId)
    }

    suspend fun disconnected(userId: String) {
        voice.leave(userId)
        onlineTime.disconnected(userId)
        matchmaking.leave(userId)
        matchPresence.disconnected(userId)
    }
}
