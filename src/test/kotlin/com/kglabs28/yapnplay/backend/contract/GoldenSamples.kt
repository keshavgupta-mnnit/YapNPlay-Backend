package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.contract.mono.MonoCardWire
import com.kglabs28.yapnplay.backend.contract.mono.MonoPublicState
import com.kglabs28.yapnplay.backend.contract.mono.MonoWireAction
import com.kglabs28.yapnplay.backend.contract.mono.MonoWireEvent
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosCardWire
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosPublicState
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosWireAction
import com.kglabs28.yapnplay.backend.contract.mono.MonoChaosWireEvent
import com.kglabs28.yapnplay.backend.contract.AdRewardStatus
import com.kglabs28.yapnplay.backend.contract.ProposeEndMatch
import com.kglabs28.yapnplay.backend.contract.CastEndVote
import com.kglabs28.yapnplay.backend.contract.EndVoteInfo
import com.kglabs28.yapnplay.backend.contract.EndVoted
import com.kglabs28.yapnplay.backend.contract.MatchPot
import com.kglabs28.yapnplay.backend.contract.League
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.contract.CoinsReward
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
        GoldenCase("HealthResponse", HealthResponse.serializer(), HealthResponse("ok", CONTRACT_VERSION, "1.0.1")),
        GoldenCase("GuestCreated", GuestCreated.serializer(), GuestCreated(USER, "s3cr3t-example-not-real")),
        GoldenCase("Me", Me.serializer(), Me(USER, "Keshav", xp = 1_400, level = 2, coins = 1_600, username = "keshav_28", userCode = "K7Q2M9", avatarId = "a03", gamesPlayed = 12, gamesWon = 5, levelXp = 1_000, nextLevelXp = 2_000, dailyBonus = 100)),
        GoldenCase("CoinsReward", CoinsReward.serializer(), CoinsReward(coins = 2_100, granted = 500, adsLeftToday = 4, dailyLimit = 5, nextAdAt = 1_760_007_200_000)),
        GoldenCase("AdRewardStatus", AdRewardStatus.serializer(), AdRewardStatus(coinsPerAd = 500, dailyLimit = 5, adsLeftToday = 4, nextAdAt = 1_760_007_200_000, serverTime = 1_760_000_000_000)),
        GoldenCase("Leagues", Leagues.serializer(), Leagues(coins = 600, leagues = listOf(League(0, "Starter", true), League(100, "Bronze", true), League(500, "Silver", true), League(1_000, "Gold", false)))),
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
        GoldenCase("CreateRoom", CreateRoom.serializer(), CreateRoom("College Crew", "dice", listOf("u-friend-1", "u-friend-2"))),
        GoldenCase("InviteToRoomBody", InviteToRoomBody.serializer(), InviteToRoomBody(listOf("u-friend-1"))),
        GoldenCase("VoteExtension", VoteExtension.serializer(), VoteExtension("match-1", keepPlaying = true)),
        GoldenCase("ExtensionVoteInfo", ExtensionVoteInfo.serializer(), ExtensionVoteInfo(1_760_000_110_000, listOf(1, 2), yes = listOf(1), extraRounds = 30)),
        GoldenCase("ExtensionVoted", ExtensionVoted.serializer(), ExtensionVoted(continued = true, yes = 2, voters = 3)),
        GoldenCase("ProposeEndMatch", ProposeEndMatch.serializer(), ProposeEndMatch("match-1")),
        GoldenCase("CastEndVote", CastEndVote.serializer(), CastEndVote("match-1", agree = true)),
        GoldenCase("EndVoteInfo", EndVoteInfo.serializer(), EndVoteInfo(proposerSeat = 2, endsAt = 1_760_000_120_000, voters = listOf(1, 2, 3), yes = listOf(2), no = emptyList())),
        GoldenCase("EndVoted", EndVoted.serializer(), EndVoted(ended = true, yes = 2, voters = 3)),
        GoldenCase("MatchPot", MatchPot.serializer(), MatchPot(entryCoins = 100, total = 400, split = listOf(50, 30, 20))),
        GoldenCase("MonoPublicState", MonoPublicState.serializer(), MonoPublicState(phase = "AWAITING_PLAY", topCard = MonoCardWire("NumberCard", "RED", 5), topCardColor = "RED", hands = mapOf(1 to listOf(MonoCardWire("SkipCard", "BLUE"), MonoCardWire("PrismDrawFour"))), cardCounts = mapOf(1 to 2, 2 to 7, 3 to 1), drawPileCount = 80, discardPileCount = 6, monoCallWindowExpiry = 1_760_000_002_500, monoWindowSeat = 3, drawnCardIndex = 1, drawnCardExpiry = 1_760_000_003_000)),
        GoldenCase("MonoWireAction", MonoWireAction.serializer(), MonoWireAction.ChooseColor("GOLD")),
        GoldenCase("MonoWireEvent", MonoWireEvent.serializer(), MonoWireEvent.CardsDrawn(seat = 2, count = 2)),
        GoldenCase("MonoChaosPublicState", MonoChaosPublicState.serializer(), MonoChaosPublicState(phase = "AWAITING_STACK_CHOICE", topCard = MonoChaosCardWire("PrismDrawSix"), topCardColor = "GREEN", hands = mapOf(2 to listOf(MonoChaosCardWire("PrismDrawTen"), MonoChaosCardWire("NumberCard", "GOLD", 7))), cardCounts = mapOf(1 to 4, 2 to 2, 3 to 0), activeStackPenalty = 10, stackTopValue = 6, eliminatedSeats = listOf(3), knockoutThreshold = 25)),
        GoldenCase("MonoChaosWireAction", MonoChaosWireAction.serializer(), MonoChaosWireAction.StackDraw(handIndex = 0)),
        GoldenCase("MonoChaosWireEvent", MonoChaosWireEvent.serializer(), MonoChaosWireEvent.CardsDiscarded(seat = 1, count = 3)),
        GoldenCase("PlayGamesSignInBody", PlayGamesSignInBody.serializer(), PlayGamesSignInBody("4/0AeanS0Z-server-auth-code")),
        GoldenCase("PlayGamesSignedIn", PlayGamesSignedIn.serializer(), PlayGamesSignedIn("u-asha", "s3cr3t-from-server", restored = true)),
        GoldenCase("InviteToRoomReply", InviteToRoomReply.serializer(), InviteToRoomReply(listOf("u-friend-1"), listOf("u-stranger"))),
        GoldenCase(
            "RoomInvites",
            RoomInvites.serializer(),
            RoomInvites(listOf(RoomInviteSummary("inv-1", "room-1", "Hostel Night", "dice", 3, FriendSummary("u-asha", "Asha", online = true, username = "asha", userCode = "K7Q2M9", avatarId = "a03"), 1_760_000_000_000))),
        ),
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
        GoldenCase("JoinQueue", JoinQueue.serializer(), JoinQueue("royal-ludo", entryCoins = 500)),
        GoldenCase("LeaveQueue", LeaveQueue.serializer(), LeaveQueue),
        GoldenCase("QueueStatus", QueueStatus.serializer(), QueueStatus("royal-ludo", 2, 4, 1_760_000_030_000, 1_760_000_005_000, entryCoins = 500)),
        GoldenCase("MatchFound", MatchFound.serializer(), MatchFound(MATCH, "royal-ludo", 2, 2, entryCoins = 500)),
        GoldenCase("QueueCancelled", QueueCancelled.serializer(), QueueCancelled("city-bazaar", QueueCancelReason.NOT_ENOUGH_PLAYERS)),
        // ── MODERATION channel ──────────────────────────────────────────
        GoldenCase("ReportPlayer", ReportPlayer.serializer(), ReportPlayer(MATCH, FRIEND, "voice_chat")),
        GoldenCase("ReportVoteRequested", ReportVoteRequested.serializer(), ReportVoteRequested("r-1", "Keshav", "Asha", 1_760_000_015_000)),
        GoldenCase("CastReportVote", CastReportVote.serializer(), CastReportVote("r-1", agree = true)),
        GoldenCase("ReportResolved", ReportResolved.serializer(), ReportResolved("r-1", true, "Asha", 2, 1, 0, 2)),
        GoldenCase("PenaltyIssued", PenaltyIssued.serializer(), PenaltyIssued(50, 1_760_000_600_000)),
    )
}
