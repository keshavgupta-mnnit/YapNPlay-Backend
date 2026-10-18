package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.api.http.HealthResponse
import com.kglabs28.yapnplay.backend.contract.GoldenFixtures.FRIEND
import com.kglabs28.yapnplay.backend.contract.GoldenFixtures.MATCH
import com.kglabs28.yapnplay.backend.contract.GoldenFixtures.REQUEST
import com.kglabs28.yapnplay.backend.contract.GoldenFixtures.ROOM
import com.kglabs28.yapnplay.backend.contract.GoldenFixtures.USER
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarPublicState
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireAction
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireEvent
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoPublicState
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoWireEvent
import com.kglabs28.yapnplay.backend.contract.transport.CONTRACT_VERSION
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.Envelope
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.RollDice
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * A sample of every message the server speaks. Each one has a golden
 * file; the app keeps the same files and the same test, so a field
 * renamed on one side breaks that side's build, not production.
 */
object GoldenSamples {
    private val f = GoldenFixtures
    private val asha = FriendSummary(FRIEND, "Asha", online = true, username = "asha.k", userCode = "P4RT8X", avatarId = "a07")

    val all: List<GoldenCase<*>> = listOf(
        // ── HTTP ────────────────────────────────────────────────────────
        GoldenCase("HealthResponse", HealthResponse.serializer(), HealthResponse("ok", CONTRACT_VERSION)),
        GoldenCase("GuestCreated", GuestCreated.serializer(), GuestCreated(USER, "s3cr3t-example-not-real")),
        GoldenCase("Me", Me.serializer(), Me(USER, "Keshav", xp = 650, level = 2, username = "keshav_28", userCode = "K7Q2M9", avatarId = "a03", gamesPlayed = 12, gamesWon = 5)),
        GoldenCase("UpdateProfileBody", UpdateProfileBody.serializer(), UpdateProfileBody(username = "keshav_28", avatarId = "a03")),
        GoldenCase("UpdateName", UpdateName.serializer(), UpdateName("Keshav")),
        GoldenCase("MyRooms", MyRooms.serializer(), MyRooms(listOf(f.summary))),
        GoldenCase("RoomPreview", RoomPreview.serializer(), RoomPreview(ROOM, "482193", "College Crew", null, 2, 1, isMember = false)),
        GoldenCase("OnlineStats", OnlineStats.serializer(), OnlineStats(1248)),
        GoldenCase("ErrorBody", ErrorBody.serializer(), ErrorBody(ErrorCode.ROOM_FULL, "This room is full.")),
        GoldenCase("RegisterDevice", RegisterDevice.serializer(), RegisterDevice("fcm-token-example", PushKeys.PLATFORM_ANDROID)),
        GoldenCase("Friends", Friends.serializer(), Friends(listOf(asha))),
        GoldenCase("FriendRequests", FriendRequests.serializer(), FriendRequests(listOf(FriendRequestSummary("fr-1", asha)))),
        GoldenCase("SendFriendRequestBody", SendFriendRequestBody.serializer(), SendFriendRequestBody("K7Q2M9")),
        GoldenCase("ActiveMatch", ActiveMatch.serializer(), ActiveMatch("m-42", "royal-ludo", "Quick Play")),
        GoldenCase("SendFriendRequestReply", SendFriendRequestReply.serializer(), SendFriendRequestReply(FriendRequestOutcomeCode.SENT)),
        GoldenCase("BlockUserBody", BlockUserBody.serializer(), BlockUserBody(FRIEND)),
        GoldenCase("BlockedUsers", BlockedUsers.serializer(), BlockedUsers(listOf(BlockedUserSummary(FRIEND, "Asha", "asha.k", "a07", 1_760_000_200_000)))),
        GoldenCase("VoiceToken", VoiceToken.serializer(), VoiceToken("wss://example.livekit.cloud", "eyJhbGciOiJIUzI1NiJ9.example.sig", 1_760_000_600_000)),
        // ── WebSocket framing ───────────────────────────────────────────
        GoldenCase(
            "Envelope",
            Envelope.serializer(),
            Envelope(REQUEST, Channel.ROOM, "JoinRoom", buildJsonObject { put("code", JsonPrimitive("482193")) }),
        ),
        GoldenCase("Ack", Ack.serializer(), Ack(REQUEST)),
        GoldenCase("Rejected", Rejected.serializer(), Rejected(REQUEST, ErrorCode.INVALID_CODE, "Room codes are 6 digits.")),
        // ── ROOM channel ────────────────────────────────────────────────
        GoldenCase("CreateRoom", CreateRoom.serializer(), CreateRoom("College Crew")),
        GoldenCase("JoinRoom", JoinRoom.serializer(), JoinRoom("482193")),
        GoldenCase("LeaveRoom", LeaveRoom.serializer(), LeaveRoom),
        GoldenCase("ExitLobby", ExitLobby.serializer(), ExitLobby),
        GoldenCase("SelectGame", SelectGame.serializer(), SelectGame("royal-ludo")),
        GoldenCase("SetBotFill", SetBotFill.serializer(), SetBotFill(true)),
        GoldenCase("SetHouseRule", SetHouseRule.serializer(), SetHouseRule("extra_turn_on_six", false)),
        GoldenCase("SetReady", SetReady.serializer(), SetReady(true)),
        GoldenCase("SetWatching", SetWatching.serializer(), SetWatching(true)),
        GoldenCase("SetVoiceState", SetVoiceState.serializer(), SetVoiceState(micOn = true, speakerOn = false)),
        GoldenCase("StartCountdown", StartCountdown.serializer(), StartCountdown),
        GoldenCase("CancelCountdown", CancelCountdown.serializer(), CancelCountdown),
        GoldenCase("RequestSnapshot", RequestSnapshot.serializer(), RequestSnapshot(7)),
        GoldenCase("TransferLeader", TransferLeader.serializer(), TransferLeader(FRIEND)),
        GoldenCase("TransferAdmin", TransferAdmin.serializer(), TransferAdmin(FRIEND)),
        GoldenCase("RemoveMember", RemoveMember.serializer(), RemoveMember(FRIEND)),
        GoldenCase("ApproveJoin", ApproveJoin.serializer(), ApproveJoin(FRIEND)),
        GoldenCase("DeclineJoin", DeclineJoin.serializer(), DeclineJoin(FRIEND)),
        GoldenCase("RoomSnapshot", RoomSnapshot.serializer(), RoomSnapshot(8, 1_760_000_000_500, f.roomInfo)),
        GoldenCase(
            "RoomSnapshotAdmin",
            RoomSnapshot.serializer(),
            RoomSnapshot(9, 1_760_000_000_600, f.roomInfo.copy(waitingList = listOf(JoinRequestInfo(FRIEND, "Asha", 1_760_000_000_100)))),
        ),
        GoldenCase("JoinRoomResult", JoinRoomResult.serializer(), JoinRoomResult(JoinStatus.JOINED, "College Crew", f.summary)),
        GoldenCase("JoinRoomResultPending", JoinRoomResult.serializer(), JoinRoomResult(JoinStatus.PENDING_APPROVAL, "College Crew")),
        GoldenCase("JoinApproved", JoinApproved.serializer(), JoinApproved(ROOM, "College Crew")),
        GoldenCase("MatchStarting", MatchStarting.serializer(), MatchStarting(MATCH)),
        // ── MATCH channel ───────────────────────────────────────────────
        GoldenCase("MatchRequestSnapshot", MatchRequestSnapshot.serializer(), MatchRequestSnapshot(MATCH, 0)),
        GoldenCase("RollDice", RollDice.serializer(), RollDice(MATCH)),
        GoldenCase("MovePiece", MovePiece.serializer(), MovePiece(MATCH, "2")),
        GoldenCase(
            "GameAction",
            GameAction.serializer(),
            GameAction(MATCH, buildJsonObject { put("type", JsonPrimitive("BuildOnTile")); put("tile", JsonPrimitive(7)) }),
        ),
        GoldenCase("SetAutoplay", SetAutoplay.serializer(), SetAutoplay(MATCH, true)),
        GoldenCase("SetMic", SetMic.serializer(), SetMic(MATCH, true)),
        GoldenCase("SetPresence", SetPresence.serializer(), SetPresence(MATCH, away = true)),
        GoldenCase("ConfirmPresence", ConfirmPresence.serializer(), ConfirmPresence(MATCH)),
        GoldenCase("LeaveSeat", LeaveSeat.serializer(), LeaveSeat(MATCH, FRIEND)),
        GoldenCase("AnswerSeatOffer", AnswerSeatOffer.serializer(), AnswerSeatOffer(MATCH, accept = true)),
        GoldenCase("ChooseSeatTaker", ChooseSeatTaker.serializer(), ChooseSeatTaker(MATCH, null)),
        GoldenCase("TakeSeat", TakeSeat.serializer(), TakeSeat(MATCH, 3)),
        GoldenCase("MatchSnapshot", MatchSnapshot.serializer(), f.ludoSnapshot),
        GoldenCase("MatchEnded", MatchEnded.serializer(), MatchEnded(listOf(RankedSeat(2, MatchSeatStatus.ACTIVE, 1), RankedSeat(1, MatchSeatStatus.ACTIVE, 2)))),
        GoldenCase("RoyalLudoPublicState", RoyalLudoPublicState.serializer(), f.ludoState),
        GoldenCase("RoyalLudoEvents", ListSerializer(RoyalLudoWireEvent.serializer()), f.ludoEvents),
        GoldenCase("CityBazaarPublicState", CityBazaarPublicState.serializer(), f.bazaarState),
        GoldenCase("CityBazaarEvents", ListSerializer(CityBazaarWireEvent.serializer()), f.bazaarEvents),
        GoldenCase("CityBazaarActions", ListSerializer(CityBazaarWireAction.serializer()), f.bazaarActions),
        // ── MATCHMAKING channel ─────────────────────────────────────────
        GoldenCase("JoinQueue", JoinQueue.serializer(), JoinQueue("royal-ludo")),
        GoldenCase("LeaveQueue", LeaveQueue.serializer(), LeaveQueue),
        GoldenCase("QueueStatus", QueueStatus.serializer(), QueueStatus("royal-ludo", 2, 4, 1_760_000_030_000, 1_760_000_005_000)),
        GoldenCase("MatchFound", MatchFound.serializer(), MatchFound(MATCH, "royal-ludo", 2, 2)),
        GoldenCase("QueueCancelled", QueueCancelled.serializer(), QueueCancelled("city-bazaar", QueueCancelReason.NOT_ENOUGH_PLAYERS)),
        // ── MODERATION channel ──────────────────────────────────────────
        GoldenCase("ReportPlayer", ReportPlayer.serializer(), ReportPlayer(MATCH, FRIEND, "voice_chat")),
        GoldenCase("ReportVoteRequested", ReportVoteRequested.serializer(), ReportVoteRequested("r-1", "Keshav", "Asha", 1_760_000_015_000)),
        GoldenCase("CastReportVote", CastReportVote.serializer(), CastReportVote("r-1", agree = true)),
        GoldenCase("ReportResolved", ReportResolved.serializer(), ReportResolved("r-1", true, "Asha", 2, 1, 0, 2)),
        GoldenCase("PenaltyIssued", PenaltyIssued.serializer(), PenaltyIssued(50, 1_760_000_600_000)),
    )
}
