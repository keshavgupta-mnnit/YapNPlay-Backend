package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.match.RoomGateway
import com.kglabs28.yapnplay.backend.contract.EndVoteInfo
import com.kglabs28.yapnplay.backend.domain.economy.PotRules
import com.kglabs28.yapnplay.backend.contract.MatchPot
import com.kglabs28.yapnplay.backend.contract.ExtensionVoteInfo
import com.kglabs28.yapnplay.backend.contract.MatchSeatStatus
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.SeatInfo
import com.kglabs28.yapnplay.backend.contract.SeatOfferInfo
import com.kglabs28.yapnplay.backend.contract.SeatOfferStageName
import com.kglabs28.yapnplay.backend.contract.SpectatorInfo
import com.kglabs28.yapnplay.backend.contract.TurnInfo
import com.kglabs28.yapnplay.backend.contract.ViewerInfo
import com.kglabs28.yapnplay.backend.contract.ViewerRole
import kotlinx.serialization.json.JsonObject

/**
 * Builds the snapshot one viewer sees: only their own legal moves, and
 * the game's per-viewer public state (no hidden information leaks).
 */
class MatchSnapshotBuilder(private val state: MatchState, private val rooms: RoomGateway) {

    fun build(viewerUserId: String, now: Long, events: List<JsonObject>): MatchSnapshot {
        val session = state.session
        val viewerSeat = state.seatOf(viewerUserId)?.seat
        val running = !session.isFinished && !state.onHold
        return MatchSnapshot(
            matchId = state.matchId,
            version = state.version,
            serverTime = now,
            viewer = if (viewerSeat != null) ViewerInfo(ViewerRole.PLAYER, viewerSeat) else ViewerInfo(ViewerRole.SPECTATOR),
            seats = state.seats.map { it.toWire() },
            publicState = session.publicState(viewerSeat),
            legalMoves = if (running) session.legalMoves(viewerSeat) else emptyList(),
            turn = TurnInfo(session.currentSeat, session.phase, if (running && !session.waitsForTick) state.deadline else null),
            gameId = state.setup.gameId,
            title = state.setup.title,
            roomId = state.roomId,
            startedAt = state.startedAt,
            spectators = state.spectators.values.map { SpectatorInfo(it.userId, it.displayName, it.micOn, it.avatarId) },
            seatOffer = if (session.isFinished) null else state.offer?.toWire(),
            events = events,
            pot = state.setup.takeIf { it.paid.isNotEmpty() }?.let { setup ->
                MatchPot(setup.entryCoins, PotRules.total(setup.entryCoins, setup.paid.size, state.seats.count { it.cpu }), setup.potSplit)
            },
            endVote = state.endVote?.let { EndVoteInfo(it.proposerSeat, it.endsAt, it.voters.sorted(), it.yes.sorted(), it.no.sorted()) },
            extensionVote = state.extensionVote?.let { vote ->
                val offer = session.extension
                ExtensionVoteInfo(vote.endsAt, vote.voters.sorted(), vote.yes.sorted(), vote.no.sorted(), offer.extraRounds, offer.extraMinutes)
            },
            mutedTalkers = state.mutedTalkers.sorted(),
            canOfferSeats = state.roomId?.let { viewerUserId == rooms.leaderOf(it) || viewerUserId == rooms.adminOf(it) } == true,
        )
    }

    private fun MatchSeatState.toWire() = SeatInfo(
        seat = seat,
        colour = colour,
        controllerUserId = userId,
        status = if (removed) MatchSeatStatus.REMOVED else MatchSeatStatus.ACTIVE,
        connected = connected,
        autoPlay = autoplay,
        consecutiveMisses = state.missedTurns[seat] ?: 0,
        cpu = cpu,
        displayName = displayName,
        level = level,
        micOn = micOn,
        presence = presence,
        avatarId = avatarId,
    )

    private fun SeatOfferState.toWire(): SeatOfferInfo = when (val s = stage) {
        is OfferStage.ConfirmingAbsence -> SeatOfferInfo(seat, leaverName, SeatOfferStageName.CONFIRMING_ABSENCE, endsAt, s.userId, s.name)
        is OfferStage.ToSpectator -> SeatOfferInfo(seat, leaverName, SeatOfferStageName.TO_SPECTATOR, endsAt, s.userId, s.name, s.round)
        is OfferStage.LeaderChoosing -> SeatOfferInfo(seat, leaverName, SeatOfferStageName.LEADER_CHOOSING, endsAt, s.userId, s.name)
        OfferStage.OpenToAll -> SeatOfferInfo(seat, leaverName, SeatOfferStageName.OPEN_TO_ALL, endsAt)
    }
}
