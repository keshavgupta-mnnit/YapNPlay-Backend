package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.voice.RelayUsageMeter
import com.kglabs28.yapnplay.backend.application.voice.VoiceHub
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinVoice
import com.kglabs28.yapnplay.backend.contract.VoicePeers
import com.kglabs28.yapnplay.backend.contract.VoiceRelayUsage
import com.kglabs28.yapnplay.backend.contract.VoiceSignal
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType

/** VOICE channel: JoinVoice answers VoicePeers; LeaveVoice, VoiceSignal and VoiceRelayUsage get an Ack. */
class VoiceChannelHandler(
    private val codec: EnvelopeCodec,
    private val hub: VoiceHub,
    private val relayUsage: RelayUsageMeter,
) {
    fun handle(userId: String, request: Envelope): Envelope = when (request.type) {
        MessageType.JOIN_VOICE -> {
            val peers = hub.join(userId, codec.payload(request, JoinVoice.serializer()).token)
            codec.reply(request, MessageType.VOICE_PEERS, VoicePeers.serializer(), peers)
        }
        MessageType.LEAVE_VOICE -> codec.ack(request).also { hub.leave(userId) }
        MessageType.VOICE_SIGNAL -> codec.ack(request).also { hub.relay(userId, codec.payload(request, VoiceSignal.serializer())) }
        MessageType.VOICE_RELAY_USAGE -> codec.ack(request).also { relayUsage.record(codec.payload(request, VoiceRelayUsage.serializer()).bytes) }
        else -> throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_TYPE)
    }
}
