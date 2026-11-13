package com.kglabs28.yapnplay.backend.application.voice

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.VoiceClosed
import com.kglabs28.yapnplay.backend.contract.VoicePeers
import com.kglabs28.yapnplay.backend.contract.VoiceSignal
import com.kglabs28.yapnplay.backend.contract.VoiceSignalKind
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock

/**
 * Who is in which voice room, and the go-between for their connection setup.
 * The audio itself goes phone to phone; the server only forwards offers and
 * answers between two people in the same voice room. A user is in one voice
 * room at a time; joining another leaves the first. Everyone there gets the
 * new list of the others ([VoicePeers]) whenever someone comes or goes.
 */
class VoiceHub(
    private val passes: VoicePasses,
    private val notifier: ClientNotifier,
    private val codec: EnvelopeCodec,
    private val clock: Clock,
    /** The admin kill switch: false refuses every join. */
    private val voiceOn: () -> Boolean = { true },
) {
    private val lock = Any()
    private val members = mutableMapOf<String, MutableSet<String>>()
    private val roomOf = mutableMapOf<String, String>()

    /** Puts [userId] in the voice room [token] is for; returns the others there. */
    fun join(userId: String, token: String): VoicePeers {
        if (!voiceOn()) throw ServiceException(ErrorCode.VOICE_UNAVAILABLE, BackendStrings.VOICE_OFF)
        val pass = passes.verify(token, clock.nowMillis())?.takeIf { it.userId == userId }
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_VOICE_PASS)
        val changed = synchronized(lock) {
            val left = removeLocked(userId)
            val room = members.getOrPut(pass.voiceRoom) { linkedSetOf() }
            if (userId !in room && room.size >= BackendConstants.VOICE_ROOM_MAX_PEERS) {
                throw ServiceException(ErrorCode.ROOM_FULL, BackendStrings.VOICE_ROOM_FULL)
            }
            room += userId
            roomOf[userId] = pass.voiceRoom
            listOfNotNull(left, pass.voiceRoom).distinct().map { it to members[it].orEmpty().toSet() }
        }
        changed.forEach { (_, people) -> announce(people, except = userId) }
        return VoicePeers(changed.first { it.first == pass.voiceRoom }.second.filter { it != userId })
    }

    /** Takes [userId] out of their voice room (left, or their connection closed). */
    fun leave(userId: String) {
        val people = synchronized(lock) {
            val room = removeLocked(userId) ?: return
            members[room].orEmpty().toSet()
        }
        announce(people)
    }

    /** Empties every voice room and tells everyone there ([VoiceClosed]): a kill switch was turned. */
    fun closeAll() {
        val people = synchronized(lock) {
            roomOf.keys.toList().also {
                roomOf.clear()
                members.clear()
            }
        }
        people.forEach { notifier.send(it, codec.push(Channel.VOICE, MessageType.VOICE_CLOSED, VoiceClosed.serializer(), VoiceClosed)) }
    }

    /** Forwards [signal] from [userId] to its peer, when both are in the same voice room. */
    fun relay(userId: String, signal: VoiceSignal) {
        if (signal.kind != VoiceSignalKind.OFFER && signal.kind != VoiceSignalKind.ANSWER) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
        }
        val together = synchronized(lock) { roomOf[userId] != null && roomOf[userId] == roomOf[signal.peer] }
        if (!together) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_VOICE_ROOM)
        notifier.send(signal.peer, codec.push(Channel.VOICE, MessageType.VOICE_SIGNAL, VoiceSignal.serializer(), signal.copy(peer = userId)))
    }

    /** The voice room [userId] was in, now without them. Call with [lock] held. */
    private fun removeLocked(userId: String): String? {
        val room = roomOf.remove(userId) ?: return null
        val people = members[room] ?: return room
        people -= userId
        if (people.isEmpty()) members.remove(room)
        return room
    }

    private fun announce(people: Set<String>, except: String? = null) {
        people.filter { it != except }.forEach { person ->
            notifier.send(person, codec.push(Channel.VOICE, MessageType.VOICE_PEERS, VoicePeers.serializer(), VoicePeers(people.filter { it != person })))
        }
    }
}
