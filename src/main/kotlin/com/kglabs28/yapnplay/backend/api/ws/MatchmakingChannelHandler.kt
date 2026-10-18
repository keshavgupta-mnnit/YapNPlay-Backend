package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.matchmaking.MatchmakingService
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinQueue
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType

/** MATCHMAKING channel: JoinQueue / LeaveQueue get an Ack; progress comes as pushes. */
class MatchmakingChannelHandler(
    private val codec: EnvelopeCodec,
    private val matchmaking: MatchmakingService,
) {
    suspend fun handle(userId: String, request: Envelope): Envelope {
        when (request.type) {
            MessageType.JOIN_QUEUE -> matchmaking.join(userId, codec.payload(request, JoinQueue.serializer()).gameId)
            MessageType.LEAVE_QUEUE -> matchmaking.leave(userId)
            else -> throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_TYPE)
        }
        return codec.ack(request)
    }
}
