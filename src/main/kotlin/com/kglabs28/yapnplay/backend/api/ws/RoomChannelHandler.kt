package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.CreateRoom
import com.kglabs28.yapnplay.backend.contract.ApproveJoin
import com.kglabs28.yapnplay.backend.contract.DeclineJoin
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.application.match.MatchPresenceUseCase
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinRoom
import com.kglabs28.yapnplay.backend.contract.JoinRoomResult
import com.kglabs28.yapnplay.backend.contract.RemoveMember
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.contract.SelectGame
import com.kglabs28.yapnplay.backend.contract.SetBotFill
import com.kglabs28.yapnplay.backend.contract.SetHouseRule
import com.kglabs28.yapnplay.backend.contract.SetReady
import com.kglabs28.yapnplay.backend.contract.SetVoiceState
import com.kglabs28.yapnplay.backend.contract.SetWatching
import com.kglabs28.yapnplay.backend.contract.TransferAdmin
import com.kglabs28.yapnplay.backend.contract.TransferLeader
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.CreatePlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.JoinPlayRoomUseCase
import com.kglabs28.yapnplay.backend.application.rooms.SendRoomActionUseCase
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.config.BackendStrings

/**
 * ROOM channel: decodes each message type and turns it into a room
 * request. The user always comes from the connection, never the payload.
 */
class RoomChannelHandler(
    private val codec: EnvelopeCodec,
    private val createRoom: CreatePlayRoomUseCase,
    private val joinRoom: JoinPlayRoomUseCase,
    private val sendAction: SendRoomActionUseCase,
    private val presence: RoomPresence,
    private val matchPresence: MatchPresenceUseCase,
) {
    suspend fun handle(userId: String, request: Envelope): Envelope = when (request.type) {
        MessageType.CREATE_ROOM -> {
            val snapshot = createRoom(userId, codec.payload(request, CreateRoom.serializer()))
            codec.reply(request, MessageType.ROOM_SNAPSHOT, RoomSnapshot.serializer(), snapshot)
        }
        MessageType.JOIN_ROOM -> {
            val result = joinRoom(userId, codec.payload(request, JoinRoom.serializer()).code)
            codec.reply(request, MessageType.JOIN_ROOM_RESULT, JoinRoomResult.serializer(), result)
        }
        MessageType.REQUEST_SNAPSHOT -> {
            val snapshot = (sendAction(userId, RoomAction.Snapshot) as RoomResult.Snapshot).snapshot
            codec.reply(request, MessageType.ROOM_SNAPSHOT, RoomSnapshot.serializer(), snapshot)
        }
        else -> {
            val roomId = presence.roomOf(userId)
            sendAction(userId, action(request))
            // Leaving the lobby also stops watching that room's match.
            if (roomId != null && (request.type == MessageType.EXIT_LOBBY || request.type == MessageType.LEAVE_ROOM)) {
                matchPresence.leftRoom(userId, roomId)
            }
            codec.ack(request)
        }
    }

    private fun action(request: Envelope): RoomAction = when (request.type) {
        MessageType.LEAVE_ROOM -> RoomAction.Leave
        MessageType.EXIT_LOBBY -> RoomAction.Exit(force = true)
        MessageType.START_COUNTDOWN -> RoomAction.StartCountdown
        MessageType.CANCEL_COUNTDOWN -> RoomAction.CancelCountdown
        MessageType.SELECT_GAME -> RoomAction.SelectGame(codec.payload(request, SelectGame.serializer()).gameId)
        MessageType.SET_BOT_FILL -> RoomAction.SetBotFill(codec.payload(request, SetBotFill.serializer()).enabled)
        MessageType.SET_READY -> RoomAction.SetReady(codec.payload(request, SetReady.serializer()).ready)
        MessageType.SET_WATCHING -> RoomAction.SetWatching(codec.payload(request, SetWatching.serializer()).watching)
        MessageType.SET_HOUSE_RULE -> codec.payload(request, SetHouseRule.serializer()).let {
            RoomAction.SetHouseRule(it.key, it.enabled)
        }
        MessageType.SET_VOICE_STATE -> codec.payload(request, SetVoiceState.serializer()).let {
            RoomAction.SetVoiceState(it.micOn, it.speakerOn)
        }
        MessageType.TRANSFER_LEADER -> RoomAction.TransferLeader(codec.payload(request, TransferLeader.serializer()).userId)
        MessageType.TRANSFER_ADMIN -> RoomAction.TransferAdmin(codec.payload(request, TransferAdmin.serializer()).userId)
        MessageType.REMOVE_MEMBER -> RoomAction.RemoveMember(codec.payload(request, RemoveMember.serializer()).userId)
        MessageType.APPROVE_JOIN -> RoomAction.ApproveJoin(codec.payload(request, ApproveJoin.serializer()).userId)
        MessageType.DECLINE_JOIN -> RoomAction.DeclineJoin(codec.payload(request, DeclineJoin.serializer()).userId)
        else -> throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_TYPE)
    }
}
