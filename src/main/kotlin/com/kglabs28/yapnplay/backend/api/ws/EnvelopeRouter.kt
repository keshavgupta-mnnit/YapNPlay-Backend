package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendStrings
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/**
 * Sends each request to its channel and always produces exactly one
 * reply.
 */
class EnvelopeRouter(
    private val codec: EnvelopeCodec,
    private val rooms: RoomChannelHandler,
    private val matches: MatchChannelHandler,
    private val matchmaking: MatchmakingChannelHandler,
    private val moderation: ModerationChannelHandler,
) {
    suspend fun handle(userId: String, request: Envelope): Envelope = try {
        when (request.channel) {
            Channel.ROOM -> rooms.handle(userId, request)
            Channel.MATCH -> matches.handle(userId, request)
            Channel.MATCHMAKING -> matchmaking.handle(userId, request)
            Channel.MODERATION -> moderation.handle(userId, request)
        }
    } catch (e: ServiceException) {
        codec.rejected(request.requestId, request.channel, e.code, e.message)
    } catch (e: SerializationException) {
        codec.rejected(request.requestId, request.channel, ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
    } catch (e: IllegalArgumentException) {
        codec.rejected(request.requestId, request.channel, ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
    } catch (e: Exception) {
        log.error("Failed to handle ${request.type}", e)
        codec.rejected(request.requestId, request.channel, ErrorCode.BAD_REQUEST, BackendStrings.INTERNAL_ERROR)
    }

    private companion object {
        val log = LoggerFactory.getLogger(EnvelopeRouter::class.java)
    }
}
