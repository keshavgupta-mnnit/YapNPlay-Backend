package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.SeatPresence

/**
 * Presence and seat handover for every game (contract "Presence and seat
 * handover"). Spectators and every handover step exist in room matches
 * only; in Quick Play a left, idle or away seat goes to the bot.
 * [publish] sends a snapshot after a change.
 */
class SeatHandover(
    private val state: MatchState,
    private val deps: MatchDeps,
    private val publish: () -> Unit,
) {
    private val isRoomMatch get() = state.roomId != null

    /** Away: the server plays for them. Back: the seat is theirs again, unless someone took it. */
    fun setAway(seat: Int, away: Boolean) {
        val player = state.seat(seat)
        if (player.removed || player.left) return
        if (away) {
            if (player.presence != SeatPresence.PLAYING) return
            state.awaySince[seat] = deps.clock.nowMillis()
            state.missedTurns[seat] = 0
            state.setPresence(seat, SeatPresence.AWAY)
        } else {
            if (player.presence == SeatPresence.PLAYING) return
            state.awaySince.remove(seat)
            state.missedTurns.remove(seat)
            if (state.offer?.seat == seat && state.offer?.stage == OfferStage.OpenToAll) state.offer = null
            state.setPresence(seat, SeatPresence.PLAYING)
        }
        publish()
    }

    /** Playing again during the "still here?" check answers it. */
    fun clearAbsenceCheck(seat: Int) {
        val offer = state.offer ?: return
        if (offer.seat != seat || offer.stage !is OfferStage.ConfirmingAbsence) return
        state.offer = null
        state.idleTurns.remove(seat)
    }

    /** "I'm still here": the check ends, the seat stays theirs. */
    fun stillHere(userId: String) {
        val offer = state.offer
        val stage = offer?.stage as? OfferStage.ConfirmingAbsence
        if (offer == null || stage?.userId != userId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NO_SEAT_OFFER)
        state.idleTurns.remove(offer.seat)
        state.offer = null
        endHold()
        publish()
    }

    /**
     * The owner gives up their seat: the bot takes it or, in a room, the
     * picked spectator is offered it. In a room the leaver keeps watching.
     */
    fun leave(seat: Int, giveTo: String?) {
        val leaver = state.seat(seat)
        state.update(seat) { copy(left = true, autoplay = false, presence = SeatPresence.AWAY) }
        state.idleTurns.remove(seat)
        if (isRoomMatch) {
            state.spectators[leaver.userId] = MatchSpectator(leaver.userId, leaver.displayName, deps.clock.nowMillis(), leaver.micOn, leaver.level)
        }
        val pick = giveTo?.let { state.spectators[it] }?.takeIf { it.userId != leaver.userId }
        if (pick != null && isRoomMatch) offerTo(seat, leaver.displayName, pick, round = 1) else toBot(seat)
    }

    /** After the bot played the owner's last turns on timeout: ask whether they are still there. */
    fun startAbsenceCheck(seat: Int) {
        val owner = state.seat(seat)
        if (state.offer != null || owner.cpu || owner.left || owner.presence != SeatPresence.PLAYING) return
        val now = deps.clock.nowMillis()
        state.offer = SeatOfferState(seat, owner.displayName, OfferStage.ConfirmingAbsence(owner.userId, owner.displayName), now + BackendConstants.ABSENCE_CHECK_MS, now)
    }

    /** Every tick: dropped connections become away, and any handover moves on. */
    fun tick(now: Long) {
        state.seats.filter { !it.connected && it.presence == SeatPresence.PLAYING && !it.left }
            .filter { now - (it.disconnectedAt ?: now) >= BackendConstants.DISCONNECT_GRACE_MS }
            .forEach { setAway(it.seat, away = true) }
        val offer = state.offer
        if (offer == null) handleAwaySeats(now) else advance(offer, now)
    }

    /** The named spectator answers. */
    fun answerOffer(userId: String, accept: Boolean) {
        val offer = state.offer
        val stage = offer?.stage as? OfferStage.ToSpectator
        if (offer == null || stage?.userId != userId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NO_SEAT_OFFER)
        if (accept) standIn(offer.seat, userId) else passed(offer, stage)
    }

    /** The leader's pick: a spectator gets the same window, the bot (null) takes it at once. */
    fun leaderChoice(userId: String, giveTo: String?) {
        val offer = state.offer
        val stage = offer?.stage as? OfferStage.LeaderChoosing
        if (offer == null || stage?.userId != userId) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NO_SEAT_OFFER)
        val pick = giveTo?.let { state.spectators[it] } ?: return toBot(offer.seat)
        offerTo(offer.seat, offer.leaverName, pick, round = 2)
    }

    /** A spectator takes a seat open to all: the first one wins. */
    fun takeSeat(userId: String, seat: Int) {
        if (userId !in state.spectators) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)
        val offer = state.offer
        if (offer == null || offer.seat != seat || offer.stage != OfferStage.OpenToAll) {
            throw ServiceException(ErrorCode.SEAT_TAKEN, BackendStrings.SEAT_TAKEN)
        }
        standIn(seat, userId)
    }

    /** Play resumes with a fresh turn timer. */
    fun endHold() {
        state.deadline = deps.clock.nowMillis() + state.session.turnSeconds * BackendConstants.MILLIS_PER_SECOND
    }

    /** Away too long: in a room the seat is open to every spectator; in Quick Play the bot takes it. */
    private fun handleAwaySeats(now: Long) {
        val seat = state.seats.firstOrNull { s ->
            s.presence == SeatPresence.AWAY && !s.removed && !s.left && (
                now - (state.awaySince[s.seat] ?: now) >= BackendConstants.OPEN_SEAT_AFTER_MS ||
                    (state.missedTurns[s.seat] ?: 0) >= BackendConstants.OPEN_SEAT_AFTER_MISSED_TURNS
                )
        } ?: return
        if (isRoomMatch && state.spectators.isNotEmpty()) {
            state.offer = SeatOfferState(seat.seat, seat.displayName, OfferStage.OpenToAll, now + BackendConstants.SEAT_CLAIM_MS, now)
            publish()
        } else {
            toBot(seat.seat)
        }
    }

    /** Timeouts: the absence check goes to the leader, unanswered offers pass on, open seats go to the bot. */
    private fun advance(offer: SeatOfferState, now: Long) {
        if (now < offer.endsAt) return
        when (val stage = offer.stage) {
            is OfferStage.ConfirmingAbsence -> {
                state.idleTurns.remove(offer.seat)
                askLeader(offer)
            }
            is OfferStage.ToSpectator -> passed(offer, stage)
            is OfferStage.LeaderChoosing -> toBot(offer.seat)
            OfferStage.OpenToAll -> toBot(offer.seat)
        }
    }

    private fun offerTo(seat: Int, leaverName: String, pick: MatchSpectator, round: Int) {
        val now = deps.clock.nowMillis()
        state.offer = SeatOfferState(seat, leaverName, OfferStage.ToSpectator(pick.userId, pick.displayName, round), now + BackendConstants.SPECTATOR_ANSWER_MS, now)
        publish()
    }

    /** Declined or ran out: the first time the leader decides, the second time the bot. */
    private fun passed(offer: SeatOfferState, stage: OfferStage.ToSpectator) {
        state.declined += stage.userId
        if (stage.round > 1) toBot(offer.seat) else askLeader(offer)
    }

    /** The room leader (or whoever has been here longest) picks; with nobody to pick, the bot takes it. */
    private fun askLeader(offer: SeatOfferState) {
        val roomId = state.roomId
        if (roomId == null || state.spectators.isEmpty()) return toBot(offer.seat)
        val present = state.seats.filter { !it.cpu && !it.left && !it.removed && it.seat != offer.seat }
            .map { Triple(it.userId, it.displayName, state.startedAt) } +
            state.spectators.values.map { Triple(it.userId, it.displayName, it.since) }
        val leader = deps.rooms.leaderOf(roomId)
        val decider = present.firstOrNull { it.first == leader } ?: present.minByOrNull { it.third } ?: return toBot(offer.seat)
        val now = deps.clock.nowMillis()
        state.offer = offer.copy(
            stage = OfferStage.LeaderChoosing(decider.first, decider.second),
            endsAt = now + BackendConstants.LEADER_CHOICE_MS,
            startedAt = now,
        )
        publish()
    }

    private fun toBot(seat: Int) {
        state.offer = null
        state.declined.clear()
        state.setPresence(seat, SeatPresence.BOT)
        endHold()
        publish()
    }

    /** A spectator takes [seat]: same colour and pieces, their name and voice. The replaced owner watches. */
    private fun standIn(seat: Int, userId: String) {
        val joiner = state.spectators.remove(userId) ?: return toBot(seat)
        val replaced = state.seat(seat)
        if (isRoomMatch && !replaced.cpu && !replaced.left) {
            state.spectators[replaced.userId] = MatchSpectator(replaced.userId, replaced.displayName, deps.clock.nowMillis(), replaced.micOn, replaced.level)
        }
        state.update(seat) {
            copy(
                userId = joiner.userId,
                displayName = joiner.displayName,
                level = joiner.level,
                cpu = false,
                micOn = joiner.micOn,
                autoplay = false,
                presence = SeatPresence.PLAYING,
                connected = true,
                left = false,
                disconnectedAt = null,
            )
        }
        state.awaySince.remove(seat)
        state.missedTurns.remove(seat)
        state.offer = null
        state.declined.clear()
        endHold()
        publish()
    }
}
