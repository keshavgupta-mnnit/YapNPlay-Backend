package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchStarting
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import kotlinx.coroutines.Job

/**
 * The 10-second start countdown. The deadline is an absolute server time
 * (sent in the snapshot with serverTime). At zero everything is checked
 * again before anyone is seated.
 */
class CountdownHandler(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val guards: RoomGuards,
    /** Asks the actor to deliver CountdownElapsed(deadline) after delayMillis. */
    private val schedule: (deadline: Long, delayMillis: Long) -> Job,
) {
    private var job: Job? = null

    suspend fun start(userId: String) {
        guards.requireLeader(userId)
        val game = guards.requireGame()
        if (state.countdownDeadline != null) return
        if (state.matchInProgress != null) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.MATCH_RUNNING)
        deps.blocks.requireNotBlocked(userId)
        if (!deps.startPolicy.canStart(state.members.values, state.room.leaderId, game, state.room.botFill)) {
            throw ServiceException(ErrorCode.NOT_ENOUGH_READY, BackendStrings.NOT_ENOUGH_READY)
        }
        val delayMillis = deps.countdownSeconds * BackendConstants.MILLIS_PER_SECOND
        val deadline = deps.clock.nowMillis() + delayMillis
        state.countdownDeadline = deadline
        job = schedule(deadline, delayMillis)
    }

    /** Any ready player (the leader counts as ready) may cancel. */
    fun cancelBy(userId: String) {
        val member = state.member(userId)
        if (state.countdownDeadline == null) return
        if (!member.ready && state.room.leaderId != userId) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NOT_READY_TO_CANCEL)
        }
        cancel()
    }

    fun cancel() {
        job?.cancel()
        job = null
        state.countdownDeadline = null
    }

    /** True while [userId] would get a seat when the running countdown ends. */
    fun isSeatedInCountdown(userId: String): Boolean =
        state.countdownDeadline != null &&
            deps.startPolicy.readyHumans(state.members.values, state.room.leaderId).any { it.userId == userId }

    /** After anything that can break the start conditions: stop the countdown if they no longer hold. */
    fun revalidate() {
        if (state.countdownDeadline == null) return
        if (!startConditionsHold()) cancel()
    }

    /** Zero reached. Returns true when a match was started. */
    suspend fun elapsed(deadline: Long): Boolean {
        if (state.countdownDeadline != deadline) return false
        state.countdownDeadline = null
        job = null
        val game = state.room.selectedGame?.let(deps.games::findEnabled) ?: return false
        if (!startConditionsHold()) return false
        val plan = deps.startPolicy.plan(
            state.members.values, state.room.leaderId, game, state.room.botFill, state.houseRules,
        )
        val matchId = deps.matchStarter.start(state.room.id, plan)
        state.matchInProgress = matchId
        state.updateAll { copy(ready = false, readyAt = null, watching = false) }
        val starting = deps.codec.push(Channel.ROOM, MessageType.MATCH_STARTING, MatchStarting.serializer(), MatchStarting(matchId))
        state.onlineIds().forEach { deps.notifier.send(it, starting) }
        return true
    }

    private fun startConditionsHold(): Boolean {
        val game = state.room.selectedGame?.let(deps.games::findEnabled) ?: return false
        return deps.startPolicy.canStart(state.members.values, state.room.leaderId, game, state.room.botFill)
    }
}
