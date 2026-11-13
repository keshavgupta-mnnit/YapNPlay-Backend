package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.Serializable

// Voice (0.16.0): peer-to-peer WebRTC. The phones send audio to each other;
// the server only passes the connection setup (offers and answers) between
// the people in one voice room, over the VOICE channel of /ws.

/** A STUN or TURN server for WebRTC (the browser's RTCIceServer shape). */
@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

/** VOICE channel: enter the voice room [token] (a `VoiceToken.token`) is for. Reply: [VoicePeers]. */
@Serializable
data class JoinVoice(val token: String)

/** The others in the caller's voice room. The reply to JoinVoice, and pushed to everyone there when it changes. */
@Serializable
data class VoicePeers(val peers: List<String>)

/**
 * Connection setup for one other person in the same voice room, whose user id is [peer]:
 * the receiver when the app sends it (Ack), the sender when the server forwards it.
 * [kind] is "offer" or "answer"; [sdp] is the full session description, its network
 * candidates included (no separate candidate messages).
 */
@Serializable
data class VoiceSignal(
    val peer: String,
    val kind: String,
    val sdp: String,
)

/**
 * VOICE channel, about once a minute while relayed: the bytes this phone's voice
 * connections sent and received through a TURN relay since its last report (Ack).
 */
@Serializable
data class VoiceRelayUsage(val bytes: Long)

/** VOICE channel push: the voice room was closed by the server (voice or the relay switched off); join again later. No payload. */
@Serializable
object VoiceClosed

/** [VoiceSignal.kind] values. */
object VoiceSignalKind {
    const val OFFER = "offer"
    const val ANSWER = "answer"
}
