package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.VoiceToken
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/**
 * Short-lived voice passes (10 minutes). Voice follows the room: members
 * of a room share its voice room; a Quick Play table gets its own. The
 * app asks again before the pass runs out.
 */
class IssueVoiceTokenUseCase(
    private val signer: VoiceTokenSigner?,
    private val rooms: PlayRoomRepository,
    private val matches: MatchRegistry,
    private val users: UserRepository,
    private val clock: Clock,
) {
    suspend fun forRoom(userId: String, roomId: String): VoiceToken {
        if (rooms.members(roomId).none { it.userId == userId }) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)
        return issue(userId, BackendConstants.VOICE_ROOM_PREFIX + roomId)
    }

    suspend fun forMatch(userId: String, matchId: String): VoiceToken {
        val match = matches.find(matchId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_SUCH_MATCH)
        if (userId !in match.presentUsers + match.seatedUsers) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)
        // A room match talks in the room's voice room; Quick Play has its own.
        val voiceRoom = match.roomId?.let { BackendConstants.VOICE_ROOM_PREFIX + it } ?: (BackendConstants.VOICE_MATCH_PREFIX + matchId)
        return issue(userId, voiceRoom)
    }

    private suspend fun issue(userId: String, voiceRoom: String): VoiceToken {
        val signer = signer ?: throw ServiceException(ErrorCode.VOICE_UNAVAILABLE, BackendStrings.VOICE_UNAVAILABLE)
        val name = users.findById(userId)?.displayName.orEmpty()
        val now = clock.nowMillis() / BackendConstants.MILLIS_PER_SECOND
        val expires = now + BackendConstants.VOICE_TOKEN_TTL_SECONDS
        val token = signer.sign(userId, name, voiceRoom, now, expires)
        return VoiceToken(signer.url, token, expires * BackendConstants.MILLIS_PER_SECOND)
    }
}
