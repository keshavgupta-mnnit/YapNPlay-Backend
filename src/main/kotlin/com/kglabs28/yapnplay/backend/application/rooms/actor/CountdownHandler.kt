package com.kglabs28.yapnplay.backend.application.rooms.actor

import org.slf4j.LoggerFactory
import com.kglabs28.yapnplay.backend.contract.CountdownStopReason
import com.kglabs28.yapnplay.backend.contract.CountdownStop
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
 * again before anyone is seated. A countdown that stops without a match
 * tells everyone why and who stopped it ([PlayRoomState.countdownStop]).
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
        // Someone came in on an older app after the game was picked: it can't start until they update.
        guards.requireEveryoneHas(game)
        if (!deps.startPolicy.canStart(state.members.values, state.room.leaderId, game, state.room.botFill)) {
            throw ServiceException(ErrorCode.NOT_ENOUGH_READY, BackendStrings.NOT_ENOUGH_READY)
        }
        val delayMillis = deps.countdownSeconds * BackendConstants.MILLIS_PER_SECOND
        val deadline = deps.clock.nowMillis() + delayMillis
        state.countdownDeadline = deadline
        state.countdownStop = null
        job = schedule(deadline, delayMillis)
    }

    /** Any ready player (the leader counts as ready) may cancel. */
    fun cancelBy(userId: String) {
        val member = state.member(userId)
        if (state.countdownDeadline == null) return
        if (!member.ready && state.room.leaderId != userId) {
            throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.NOT_READY_TO_CANCEL)
        }
        stop(CountdownStopReason.CANCELLED, userId, member.displayName)
    }

    /** Stops a running countdown because of [byUserId] (named [byName]); everyone is told. */
    fun stop(reason: CountdownStopReason, byUserId: String?, byName: String? = byUserId?.let { state.members[it]?.displayName }) {
        if (state.countdownDeadline == null) return
        cancel()
        state.countdownStop = CountdownStop(reason, byUserId, byName, deps.clock.nowMillis())
    }

    /** Stops it without telling anyone (the room is going away). */
    fun cancel() {
        job?.cancel()
        job = null
        state.countdownDeadline = null
    }

    /** True while [userId] would get a seat when the running countdown ends. */
    fun isSeatedInCountdown(userId: String): Boolean =
        state.countdownDeadline != null &&
            deps.startPolicy.readyHumans(state.members.values, state.room.leaderId).any { it.userId == userId }

    /** After [byUserId]'s action that can break the start conditions: stop the countdown if they no longer hold. */
    fun revalidate(reason: CountdownStopReason, byUserId: String, byName: String? = state.members[byUserId]?.displayName) {
        if (state.countdownDeadline == null) return
        if (!startConditionsHold()) stop(reason, byUserId, byName)
    }

    /** Zero reached. Returns true when a match was started. */
    suspend fun elapsed(deadline: Long): Boolean {
        if (state.countdownDeadline != deadline) return false
        state.countdownDeadline = null
        job = null
        val game = state.room.selectedGame?.let(deps.games::findEnabled)
        if (game == null || !startConditionsHold()) return failedToStart()
        guards.withoutGame(game).takeIf { it.isNotEmpty() }?.let { missing ->
            state.countdownStop = CountdownStop(CountdownStopReason.UPDATE_NEEDED, byName = missing.joinToString(), at = deps.clock.nowMillis())
            return false
        }
        val plan = deps.startPolicy.plan(
            state.members.values, state.room.leaderId, game, state.room.botFill, state.houseRules, state.room.entryCoins,
        )
        val matchId = try {
            deps.matchStarter.start(state.room.id, plan)
        } catch (e: ServiceException) {
            log.warn("Room ${state.room.id}: the match did not start (${e.message})")
            return failedToStart()
        } catch (e: Exception) {
            log.error("Room ${state.room.id}: starting the match failed", e)
            return failedToStart()
        }
        state.matchInProgress = matchId
        state.updateAll { copy(ready = false, readyAt = null, watching = false) }
        val starting = deps.codec.push(Channel.ROOM, MessageType.MATCH_STARTING, MatchStarting.serializer(), MatchStarting(matchId))
        state.onlineIds().forEach { deps.notifier.send(it, starting) }
        return true
    }

    /** The countdown ran out but no match could start: everyone is told it was the server, not a player. */
    private fun failedToStart(): Boolean {
        state.countdownStop = CountdownStop(CountdownStopReason.START_FAILED, at = deps.clock.nowMillis())
        return false
    }

    private fun startConditionsHold(): Boolean {
        val game = state.room.selectedGame?.let(deps.games::findEnabled) ?: return false
        return deps.startPolicy.canStart(state.members.values, state.room.leaderId, game, state.room.botFill)
    }
}

private val log = LoggerFactory.getLogger(CountdownHandler::class.java)
