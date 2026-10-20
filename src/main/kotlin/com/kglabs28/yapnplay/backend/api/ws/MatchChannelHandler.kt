package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.contract.VoteExtension
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.SendMatchActionUseCase
import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.application.match.actor.MatchResult
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.AnswerSeatOffer
import com.kglabs28.yapnplay.backend.contract.ChooseSeatTaker
import com.kglabs28.yapnplay.backend.contract.ConfirmPresence
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.LeaveSeat
import com.kglabs28.yapnplay.backend.contract.MatchRequestSnapshot
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.SetAutoplay
import com.kglabs28.yapnplay.backend.contract.SetMic
import com.kglabs28.yapnplay.backend.contract.SetPresence
import com.kglabs28.yapnplay.backend.contract.TakeSeat
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * MATCH channel: every message names its match; the player is always the
 * connection's user. Game moves (RollDice, MovePiece, GameAction) go to
 * the game; the rest is presence and seat handover.
 */
class MatchChannelHandler(
    private val codec: EnvelopeCodec,
    private val send: SendMatchActionUseCase,
    private val users: UserRepository,
) {
    suspend fun handle(userId: String, request: Envelope): Envelope = when (request.type) {
        MessageType.MATCH_REQUEST_SNAPSHOT -> {
            val body = codec.payload(request, MatchRequestSnapshot.serializer())
            val user = users.findById(userId)
            val action = MatchAction.RequestSnapshot(user?.displayName.orEmpty(), user?.avatarId.orEmpty())
            val result = send(userId, body.matchId, action) as MatchResult.Snapshot
            codec.reply(request, MessageType.MATCH_SNAPSHOT, MatchSnapshot.serializer(), result.snapshot)
        }
        MessageType.ROLL_DICE, MessageType.MOVE_PIECE, MessageType.GAME_ACTION -> {
            val matchId = request.payload[MATCH_ID]?.jsonPrimitive?.contentOrNull
                ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_REQUEST)
            send(userId, matchId, MatchAction.Play(request.type, request.payload))
            codec.ack(request)
        }
        else -> {
            val (matchId, action) = control(request)
            send(userId, matchId, action)
            codec.ack(request)
        }
    }

    private fun control(request: Envelope): Pair<String, MatchAction> = when (request.type) {
        MessageType.SET_AUTOPLAY -> codec.payload(request, SetAutoplay.serializer()).let { it.matchId to MatchAction.SetAutoplay(it.enabled) }
        MessageType.SET_MIC -> codec.payload(request, SetMic.serializer()).let { it.matchId to MatchAction.SetMic(it.micOn) }
        MessageType.SET_PRESENCE -> codec.payload(request, SetPresence.serializer()).let { it.matchId to MatchAction.SetPresence(it.away) }
        MessageType.CONFIRM_PRESENCE -> codec.payload(request, ConfirmPresence.serializer()).let { it.matchId to MatchAction.ConfirmPresence }
        MessageType.LEAVE_SEAT -> codec.payload(request, LeaveSeat.serializer()).let { it.matchId to MatchAction.Leave(it.giveToUserId) }
        MessageType.ANSWER_SEAT_OFFER -> codec.payload(request, AnswerSeatOffer.serializer()).let { it.matchId to MatchAction.AnswerOffer(it.accept) }
        MessageType.CHOOSE_SEAT_TAKER -> codec.payload(request, ChooseSeatTaker.serializer()).let { it.matchId to MatchAction.ChooseTaker(it.giveToUserId) }
        MessageType.TAKE_SEAT -> codec.payload(request, TakeSeat.serializer()).let { it.matchId to MatchAction.TakeSeat(it.seat) }
        MessageType.VOTE_EXTENSION -> codec.payload(request, VoteExtension.serializer()).let { it.matchId to MatchAction.VoteExtension(it.keepPlaying) }
        else -> throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_TYPE)
    }

    private companion object {
        const val MATCH_ID = "matchId"
    }
}
