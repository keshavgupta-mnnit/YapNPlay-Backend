package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.match.MatchSetup
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.SeatPresence
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * What a running match saves after every change, so a restart can bring
 * it back: seats, turn bookkeeping and the game's own state ([game]).
 * Spectators and handover offers are not kept: watchers ask again, and a
 * seat whose handover was holding play goes to the bot.
 */
@Serializable
data class MatchCheckpoint(
    val setup: MatchSetup,
    val startedAt: Long,
    val seats: List<MatchSeatState>,
    val version: Long,
    val turnOwner: Int,
    val turnTimedOut: Boolean,
    val turnActed: Boolean,
    val awaySince: Map<Int, Long>,
    val missedTurns: Map<Int, Int>,
    val idleTurns: Map<Int, Int>,
    /** The seat whose handover held play when this was saved, if any. */
    val heldSeat: Int? = null,
    val game: JsonObject,
) {
    /**
     * The match as it comes back at [now]: every human counts as
     * disconnected (the away rules apply until they ask for a snapshot)
     * and the current turn gets a fresh timer.
     */
    fun restore(session: GameSession, now: Long): MatchState {
        val restored = seats.map { seat ->
            val back = if (seat.cpu || seat.left) seat else seat.copy(connected = false, disconnectedAt = now)
            if (back.seat == heldSeat) back.copy(presence = SeatPresence.BOT) else back
        }
        return MatchState(setup, session, restored.toMutableList(), startedAt).also { state ->
            state.version = version
            state.deadline = now + session.turnSeconds * BackendConstants.MILLIS_PER_SECOND
            state.turnOwner = turnOwner
            state.turnTimedOut = turnTimedOut
            state.turnActed = turnActed
            state.awaySince += awaySince
            state.missedTurns += missedTurns
            state.idleTurns += idleTurns
        }
    }

    companion object {
        fun of(state: MatchState): MatchCheckpoint = MatchCheckpoint(
            setup = state.setup,
            startedAt = state.startedAt,
            seats = state.seats.toList(),
            version = state.version,
            turnOwner = state.turnOwner,
            turnTimedOut = state.turnTimedOut,
            turnActed = state.turnActed,
            awaySince = state.awaySince.toMap(),
            missedTurns = state.missedTurns.toMap(),
            idleTurns = state.idleTurns.toMap(),
            heldSeat = state.offer?.takeIf { it.stage.holdsMatch }?.seat,
            game = state.session.save(),
        )
    }
}
