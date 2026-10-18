package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings

/** Size limit, parsing, duplicate requestIds and the rate limit, before a message reaches a channel. */
class InboundGate(
    private val codec: EnvelopeCodec,
    private val limiter: MessageRateLimiter,
    private val replies: RecentReplies,
) {
    suspend fun process(text: String, handle: suspend (Envelope) -> Envelope): Envelope {
        if (text.toByteArray(Charsets.UTF_8).size > BackendConstants.WS_MAX_MESSAGE_BYTES) {
            return codec.rejected(NO_REQUEST_ID, Channel.ROOM, ErrorCode.BAD_REQUEST, BackendStrings.MESSAGE_TOO_LARGE)
        }
        val request = runCatching { codec.decode(text) }.getOrNull()
            ?: return codec.rejected(NO_REQUEST_ID, Channel.ROOM, ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        replies.find(request.requestId)?.let { return it }
        if (!limiter.tryAcquire()) {
            return codec.rejected(request.requestId, request.channel, ErrorCode.RATE_LIMITED, BackendStrings.RATE_LIMITED)
        }
        return handle(request).also { replies.remember(request.requestId, it) }
    }

    private companion object {
        const val NO_REQUEST_ID = ""
    }
}
