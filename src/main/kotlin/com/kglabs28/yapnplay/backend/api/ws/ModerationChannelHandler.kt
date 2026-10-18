package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.moderation.ModerationService
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.CastReportVote
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.ReportPlayer
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType

/** MODERATION channel: ReportPlayer and CastReportVote get an Ack; results come as pushes. */
class ModerationChannelHandler(
    private val codec: EnvelopeCodec,
    private val moderation: ModerationService,
) {
    suspend fun handle(userId: String, request: Envelope): Envelope {
        when (request.type) {
            MessageType.REPORT_PLAYER -> moderation.report(userId, codec.payload(request, ReportPlayer.serializer()))
            MessageType.CAST_REPORT_VOTE -> codec.payload(request, CastReportVote.serializer()).let {
                moderation.vote(userId, it.reportId, it.agree)
            }
            else -> throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_TYPE)
        }
        return codec.ack(request)
    }
}
