package com.kglabs28.yapnplay.backend.contract

import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarBank
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarAuction
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarDebt
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarPublicState
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarSeatState
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireAction
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireEvent
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoPublicState
import com.kglabs28.yapnplay.backend.contract.royalludo.RoyalLudoWireEvent
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.serialization.json.jsonObject

/** Fixed sample values the golden files are made from (fake ids, fixed times). */
object GoldenFixtures {
    const val USER = "10ab793c-9faf-47a5-81ec-82076ca8713f"
    const val FRIEND = "5d2f1e44-0c1b-4b8e-9a51-3f7c2d8e6a10"
    const val ROOM = "8b3a9c61-2f4d-4e7a-b1c5-6d9e0f1a2b3c"
    const val MATCH = "3e1d2c4b-5a69-4788-9abc-def012345678"
    const val REQUEST = "req-1"

    val summary = RoomSummary(ROOM, "482193", "College Crew", 2, 1, isLeader = true, icon = "group")

    val roomInfo = RoomInfo(
        id = ROOM,
        code = "482193",
        name = "College Crew",
        leaderId = USER,
        adminId = USER,
        description = null,
        icon = "group",
        members = listOf(
            RoomMember(USER, "Keshav", Presence.ONLINE, ready = false, micOn = true, speakerOn = true, onlineSince = 1_760_000_000_000, avatarId = "a03"),
            RoomMember(FRIEND, "Asha", Presence.OFFLINE, ready = false, isFriend = true),
        ),
        selectedGame = "royal-ludo",
        botFill = true,
        houseRules = mapOf("extra_turn_on_six" to true),
        countdownDeadline = 1_760_000_010_000,
        matchInProgress = null,
    )

    val ludoState = RoyalLudoPublicState(
        pieces = mapOf(1 to listOf(-1, 5, 56, 52), 2 to listOf(13, -1, -1, -1)),
        phase = "MOVE",
        lastRoll = 6,
        legalPieces = listOf(0, 1),
    )

    val ludoEvents = listOf(
        RoyalLudoWireEvent.Rolled(1, 6),
        RoyalLudoWireEvent.Moved(1, 1, 5, 11),
        RoyalLudoWireEvent.Captured(1, 2, 0),
        RoyalLudoWireEvent.TurnPassed(1),
        RoyalLudoWireEvent.Reverted(1, mapOf(1 to listOf(-1, 5, 56, 52))),
    )

    val ludoSnapshot = MatchSnapshot(
        matchId = MATCH,
        version = 12,
        serverTime = 1_760_000_100_000,
        viewer = ViewerInfo(ViewerRole.PLAYER, 1),
        seats = listOf(
            SeatInfo(1, Colour.RED, USER, MatchSeatStatus.ACTIVE, connected = true, autoPlay = false, consecutiveMisses = 0, displayName = "Keshav", level = 3, micOn = true),
            SeatInfo(2, Colour.GREEN, "cpu-1", MatchSeatStatus.ACTIVE, connected = true, autoPlay = false, consecutiveMisses = 0, cpu = true, displayName = "CPU 1"),
        ),
        publicState = testJson.encodeToJsonElement(RoyalLudoPublicState.serializer(), ludoState).jsonObject,
        legalMoves = listOf(LegalMove.MovePiece("0"), LegalMove.MovePiece("1")),
        turn = TurnInfo(1, "MOVE", 1_760_000_130_000),
        gameId = "royal-ludo",
        title = "College Crew",
        roomId = ROOM,
        startedAt = 1_760_000_000_000,
        spectators = listOf(SpectatorInfo(FRIEND, "Asha")),
        seatOffer = SeatOfferInfo(2, "Neha", SeatOfferStageName.TO_SPECTATOR, 1_760_000_130_000, FRIEND, "Asha", 1),
        events = ludoEvents.take(2).map { testJson.encodeToJsonElement(RoyalLudoWireEvent.serializer(), it).jsonObject },
    )

    val bazaarState = CityBazaarPublicState(
        players = listOf(
            CityBazaarSeatState(1, 14_500, 7, inJail = false, halfRent = false, bankrupt = false, rentCollected = 300),
            CityBazaarSeatState(2, 0, 10, inJail = true, halfRent = true, bankrupt = false),
        ),
        owners = mapOf(7 to 1),
        levels = mapOf(7 to 2),
        phase = "AUCTION",
        die1 = 3,
        die2 = 4,
        debt = CityBazaarDebt(1_200, "RENT", 7),
        jailTriesLeft = 2,
        auction = CityBazaarAuction(9, 2, 1_000, 1, 1_100, 1_760_000_105_000),
        timeEndsAt = 1_760_001_800_000,
        round = 4,
        roundLimit = 30,
        netWorth = mapOf(1 to 21_000, 2 to 6_500),
        rentCollected = mapOf(7 to 300),
        bank = CityBazaarBank(cash = 298_500, received = 2_000, paid = 3_500),
    )

    val bazaarEvents = listOf(
        CityBazaarWireEvent.Rolled(1, 3, 4),
        CityBazaarWireEvent.Moved(1, 0, 7, 7, jump = false),
        CityBazaarWireEvent.Money(1, 2, 300, "RENT", 7),
        CityBazaarWireEvent.Card(1, "card-tax-refund"),
        CityBazaarWireEvent.AuctionEnded(2, 9, null, 1_000),
        CityBazaarWireEvent.Rested(2),
    )

    val bazaarActions = listOf(
        CityBazaarWireAction.Roll,
        CityBazaarWireAction.Buy,
        CityBazaarWireAction.Build(7),
        CityBazaarWireAction.StartAuction(9),
        CityBazaarWireAction.Bid,
        CityBazaarWireAction.DeclareBankruptcy,
    )
}
