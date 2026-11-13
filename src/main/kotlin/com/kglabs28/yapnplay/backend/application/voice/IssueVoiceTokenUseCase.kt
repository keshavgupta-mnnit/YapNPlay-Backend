package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.VoiceToken
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/**
 * Short-lived voice passes (10 minutes). Members of a room share its
 * lobby's voice room; every match has its own, for its players and
 * spectators only, so the lobby never hears the table (or the other way
 * round). A Nearby party lives on its host's phone, so the server can't
 * check who is in it: its random id (from the phone) is the voice room,
 * and only those who know the id find it. The app asks again before the
 * pass runs out. The pass is used with JoinVoice on the VOICE channel. The admin
 * kill switches ([VoicePolicy]) refuse passes (voice off) or leave out the relay.
 */
class IssueVoiceTokenUseCase(
    private val passes: VoicePasses,
    private val iceServers: IceServerSource,
    private val policy: VoicePolicy,
    private val rooms: PlayRoomRepository,
    private val matches: MatchRegistry,
    private val clock: Clock,
) {
    suspend fun forRoom(userId: String, roomId: String): VoiceToken {
        if (rooms.members(roomId).none { it.userId == userId }) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)
        return issue(userId, BackendConstants.VOICE_ROOM_PREFIX + roomId)
    }

    suspend fun forMatch(userId: String, matchId: String): VoiceToken {
        val match = matches.find(matchId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_SUCH_MATCH)
        if (userId !in match.presentUsers + match.seatedUsers) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)
        return issue(userId, BackendConstants.VOICE_MATCH_PREFIX + matchId)
    }

    suspend fun forNearby(userId: String, partyId: String): VoiceToken {
        if (!BackendConstants.NEARBY_PARTY_ID.matches(partyId)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NOT_A_NEARBY_PARTY)
        return issue(userId, partyId)
    }

    private suspend fun issue(userId: String, voiceRoom: String): VoiceToken {
        if (!policy.voiceOn()) throw ServiceException(ErrorCode.VOICE_UNAVAILABLE, BackendStrings.VOICE_OFF)
        val expiresAt = clock.nowMillis() + BackendConstants.VOICE_TOKEN_TTL_SECONDS * BackendConstants.MILLIS_PER_SECOND
        val token = passes.sign(VoicePass(userId, voiceRoom, expiresAt))
        return VoiceToken(BackendConstants.WS_PATH, token, expiresAt, iceServers.current(withRelay = policy.relayAllowed()))
    }
}
