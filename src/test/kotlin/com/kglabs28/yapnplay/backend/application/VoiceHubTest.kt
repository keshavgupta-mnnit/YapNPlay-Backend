package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.voice.VoiceHub
import com.kglabs28.yapnplay.backend.application.voice.VoicePass
import com.kglabs28.yapnplay.backend.application.voice.VoicePasses
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.VoicePeers
import com.kglabs28.yapnplay.backend.contract.VoiceSignal
import com.kglabs28.yapnplay.backend.contract.VoiceSignalKind
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.voice.VoiceSession
import com.kglabs28.yapnplay.backend.support.RecordingNotifier
import com.kglabs28.yapnplay.backend.support.testJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceHubTest {
    private val now = 1_000_000L
    private val passes = VoicePasses()
    private val notifier = RecordingNotifier()
    private val codec = EnvelopeCodec(testJson) { "push" }
    private val hub = VoiceHub(passes, notifier, codec, Clock { now })

    private fun pass(user: String, room: String, expiresAt: Long = now + 60_000) = passes.sign(VoicePass(user, room, expiresAt))

    private fun lastPeersOf(user: String): List<String> = notifier.sent.last { it.first == user && it.second.type == MessageType.VOICE_PEERS }
        .let { codec.payload(it.second, VoicePeers.serializer()).peers }

    @Test
    fun `a pass reads back and refuses forgery and expiry`() {
        val token = pass("a", "room-1")
        assertEquals(VoicePass("a", "room-1", now + 60_000), passes.verify(token, now))
        assertNull(passes.verify(token, now + 60_000))
        assertNull(passes.verify(token.dropLast(2) + "xx", now))
        assertNull(VoicePasses().verify(token, now))
    }

    @Test
    fun `joining tells the newcomer who is there and everyone else the new list`() {
        assertEquals(emptyList(), hub.join("a", pass("a", "room-1")).peers)
        assertEquals(listOf("a"), hub.join("b", pass("b", "room-1")).peers)
        assertEquals(listOf("b"), lastPeersOf("a"))
        hub.leave("b")
        assertEquals(emptyList(), lastPeersOf("a"))
    }

    @Test
    fun `another person's pass or another room's member is refused`() {
        assertFailsWith<ServiceException> { hub.join("a", pass("b", "room-1")) }
        hub.join("a", pass("a", "room-1"))
        hub.join("c", pass("c", "room-2"))
        val error = assertFailsWith<ServiceException> { hub.relay("a", VoiceSignal("c", VoiceSignalKind.OFFER, "sdp")) }
        assertEquals(ErrorCode.NOT_MEMBER, error.code)
    }

    @Test
    fun `a signal reaches its peer with the sender as peer`() {
        hub.join("a", pass("a", "room-1"))
        hub.join("b", pass("b", "room-1"))
        hub.relay("b", VoiceSignal("a", VoiceSignalKind.OFFER, "offer-sdp"))
        val forwarded = notifier.sent.last { it.first == "a" && it.second.type == MessageType.VOICE_SIGNAL }
        assertEquals(VoiceSignal("b", VoiceSignalKind.OFFER, "offer-sdp"), codec.payload(forwarded.second, VoiceSignal.serializer()))
    }

    @Test
    fun `the kill switch refuses joins and closing empties every voice room`() {
        var on = true
        val switched = VoiceHub(passes, notifier, codec, Clock { now }, voiceOn = { on })
        switched.join("a", pass("a", "room-1"))
        switched.join("b", pass("b", "room-1"))
        switched.closeAll()
        assertTrue(notifier.sent.any { it.first == "a" && it.second.type == MessageType.VOICE_CLOSED })
        assertEquals(emptyList(), switched.join("a", pass("a", "room-1")).peers)
        on = false
        assertEquals(ErrorCode.VOICE_UNAVAILABLE, assertFailsWith<ServiceException> { switched.join("b", pass("b", "room-1")) }.code)
    }

    @Test
    fun `a stretch in a voice room is recorded when it ends, rejoining the same room keeps it going`() {
        var clock = now
        val sessions = mutableListOf<VoiceSession>()
        val tracked = VoiceHub(passes, notifier, codec, Clock { clock }, onSessionEnd = { sessions += it })
        tracked.join("a", pass("a", "room-1"))
        clock += 30_000
        tracked.join("a", pass("a", "room-1"))
        clock += 30_000
        tracked.leave("a")
        assertEquals(listOf(VoiceSession("a", now, now + 60_000)), sessions)
    }

    @Test
    fun `joining another voice room leaves the first`() {
        hub.join("a", pass("a", "room-1"))
        hub.join("b", pass("b", "room-1"))
        hub.join("b", pass("b", "match-9"))
        assertEquals(emptyList(), lastPeersOf("a"))
        assertTrue(hub.join("c", pass("c", "match-9")).peers == listOf("b"))
    }
}
