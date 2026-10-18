package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchOutcomeStatus
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import com.kglabs28.yapnplay.backend.domain.match.FinishedSeat
import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchEnded
import com.kglabs28.yapnplay.backend.contract.MatchSeatStatus
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.RankedSeat
import com.kglabs28.yapnplay.backend.contract.SeatPresence
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel as Inbox
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * One live match. Like a room, it is an actor: one worker, one inbox, so
 * moves, timers and handovers never race. The server is the referee: it
 * rolls, validates, plays bots and timed-out turns, and sends every
 * viewer their own snapshot (with the events to animate) after each change.
 * Each change is also saved ([MatchCheckpoint]) so a restart can bring
 * the match back.
 */
class MatchActor(
    private val state: MatchState,
    private val deps: MatchDeps,
    private val scope: CoroutineScope,
    /** Called once the match is over and has been kept around long enough. */
    private val onClosed: (matchId: String) -> Unit,
) {
    val matchId: String = state.matchId
    val roomId: String? = state.roomId
    val gameId: String = state.setup.gameId
    val title: String = state.setup.title
    val isFinished: Boolean get() = state.ended

    private val inbox = Inbox<MatchCommand>(Inbox.UNLIMITED)
    private val handover = SeatHandover(state, deps) { publish(emptyList()) }
    private val snapshots = MatchSnapshotBuilder(state)
    private var wakeToken = 0L
    /** Something changed since the last wake was planned (set by publish, cleared by scheduleWake). */
    private var rescheduleNeeded = true
    /** Timer runs (ticks, wakes) in a row that threw; too many and the match is aborted. */
    private var failuresInARow = 0
    /** The latest checkpoint to save; null means the match is over and its row goes. Older ones are skipped. */
    private val checkpoints = Inbox<MatchCheckpoint?>(Inbox.CONFLATED)
    private val saver: Job = scope.launch { for (checkpoint in checkpoints) persist(checkpoint) }
    private val worker: Job = scope.launch { for (command in inbox) process(command) }
    private val ticker: Job = scope.launch {
        while (isActive) {
            delay(BackendConstants.MATCH_TICK_MS)
            inbox.trySend(MatchCommand.Tick)
        }
    }

    init {
        // Saved from the start, so even a match nobody has moved in yet survives a restart.
        checkpoints.trySend(MatchCheckpoint.of(state))
        // The first tick sends nothing new but starts the first turn's timer.
        inbox.trySend(MatchCommand.Tick)
    }

    suspend fun ask(userId: String, action: MatchAction): MatchResult {
        val reply = CompletableDeferred<MatchResult>()
        return try {
            inbox.send(MatchCommand.Request(userId, action, reply))
            reply.await()
        } catch (e: ClosedSendChannelException) {
            MatchResult.Failed(ErrorCode.MATCH_ENDED, BackendStrings.MATCH_ENDED)
        }
    }

    /** Human seats' owners (for "already in a match" checks); refreshed after every change. */
    @Volatile
    var seatedUsers: Set<String> = state.seats.filter { !it.cpu && !it.left }.map { it.userId }.toSet()
        private set

    /** Everyone at the table right now, players and spectators (moderation voters). */
    @Volatile
    var presentUsers: Set<String> = seatedUsers
        private set

    /** Stops play; the last checkpoint is still written ([awaitSaved]). */
    fun stop() {
        ticker.cancel()
        inbox.close()
        worker.cancel()
        checkpoints.close()
    }

    /** Waits until every checkpoint handed over before [stop] is written. */
    suspend fun awaitSaved() = saver.join()

    private suspend fun process(command: MatchCommand) {
        when (command) {
            is MatchCommand.Request -> command.reply.complete(handle(command.userId, command.action))
            is MatchCommand.Wake -> runTimer(command) { if (command.token == wakeToken) playDueTurn() }
            MatchCommand.Tick -> runTimer(command) { tick() }
        }
    }

    /** A tick or wake that throws is counted; when they keep failing, play cannot go on. */
    private fun runTimer(command: MatchCommand, block: () -> Unit) {
        try {
            block()
            failuresInARow = 0
        } catch (e: Exception) {
            log.error("Match $matchId failed on ${command::class.simpleName}", e)
            recordError()
            if (++failuresInARow >= BackendConstants.MATCH_FAILURES_BEFORE_ABORT && !state.ended) abort(e)
        }
    }

    private fun recordError() {
        scope.launch { runCatching { deps.outcomes.errorSeen(matchId) }.onFailure { log.warn("Match $matchId: counting the error failed", it) } }
    }

    /** The match is broken: it ends without a result and the room is freed. Players' next request finds no match. */
    private fun abort(cause: Exception) {
        state.ended = true
        state.offer = null
        ticker.cancel()
        checkpoints.trySend(null)
        checkpoints.close()
        val reason = BackendStrings.matchBroke(cause::class.simpleName, cause.message)
        scope.launch {
            runCatching { deps.outcomes.ended(matchId, MatchOutcomeStatus.ABORTED_ERROR, deps.clock.nowMillis(), reason) }
                .onFailure { log.warn("Match $matchId: recording the abort failed", it) }
            roomId?.let { room -> runCatching { deps.rooms.matchEnded(room, matchId) } }
            onClosed(matchId)
            stop()
        }
    }

    private fun handle(userId: String, action: MatchAction): MatchResult = try {
        if (state.ended && action !is MatchAction.RequestSnapshot) throw ServiceException(ErrorCode.MATCH_ENDED, BackendStrings.MATCH_ENDED)
        when (action) {
            is MatchAction.Play -> play(userId, action)
            is MatchAction.RequestSnapshot -> return MatchResult.Snapshot(snapshotFor(userId, action.displayName, action.avatarId))
            is MatchAction.SetAutoplay -> setAutoplay(userId, action.enabled)
            is MatchAction.SetMic -> setMic(userId, action.on)
            is MatchAction.SetPresence -> handover.setAway(ownSeat(userId).seat, action.away)
            MatchAction.ConfirmPresence -> handover.stillHere(userId)
            is MatchAction.Leave -> handover.leave(ownSeat(userId).seat, action.giveToUserId)
            is MatchAction.AnswerOffer -> handover.answerOffer(userId, action.accept)
            is MatchAction.ChooseTaker -> handover.leaderChoice(userId, action.giveToUserId)
            is MatchAction.TakeSeat -> handover.takeSeat(userId, action.seat)
            MatchAction.Disconnected -> disconnected(userId)
            MatchAction.StopWatching -> if (state.spectators.remove(userId) != null) publish(emptyList())
        }
        scheduleWake()
        MatchResult.Done
    } catch (e: ServiceException) {
        MatchResult.Failed(e.code, e.message)
    } catch (e: Exception) {
        log.error("Match $matchId failed on ${action::class.simpleName}", e)
        recordError()
        MatchResult.Failed(ErrorCode.BAD_REQUEST, BackendStrings.INTERNAL_ERROR)
    }

    private fun play(userId: String, action: MatchAction.Play) {
        val seat = state.seatOf(userId) ?: throw ServiceException(ErrorCode.NOT_YOUR_TURN, BackendStrings.NOT_IN_MATCH)
        if (state.onHold) throw ServiceException(ErrorCode.NOT_YOUR_TURN, BackendStrings.MATCH_ON_HOLD)
        val session = state.session
        val move = session.decode(action.type, action.payload)
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_ACTION)
        if (seat.seat != session.currentSeat && !session.waitsForTick) {
            throw ServiceException(ErrorCode.NOT_YOUR_TURN, BackendStrings.NOT_YOUR_TURN)
        }
        val events = session.apply(seat.seat, move) ?: throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.ILLEGAL_MOVE)
        if (seat.seat == state.turnOwner) state.turnActed = true
        handover.clearAbsenceCheck(seat.seat)
        // Acting means they are back at the table.
        if (seat.presence == SeatPresence.AWAY) handover.setAway(seat.seat, away = false)
        publish(events)
    }

    /** A player's snapshot; a room member without a seat becomes a spectator. */
    private fun snapshotFor(userId: String, displayName: String, avatarId: String = ""): MatchSnapshot {
        val seat = state.seatOf(userId)
        if (seat != null) {
            if (!seat.connected) {
                state.update(seat.seat) { copy(connected = true, disconnectedAt = null) }
                publish(emptyList())
            }
        } else if (userId !in state.spectators) {
            val roomId = state.roomId
            if (state.ended || roomId == null || !deps.rooms.isPresent(userId, roomId)) {
                throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)
            }
            state.spectators[userId] = MatchSpectator(userId, displayName, deps.clock.nowMillis(), avatarId = avatarId)
            publish(emptyList())
        }
        return snapshots.build(userId, deps.clock.nowMillis(), emptyList())
    }

    private fun setAutoplay(userId: String, enabled: Boolean) {
        val seat = ownSeat(userId)
        // Autoplay off on a seat the bot took (nobody else did): it is theirs again.
        state.update(seat.seat) {
            if (!enabled && presence == SeatPresence.BOT) copy(autoplay = false, presence = SeatPresence.PLAYING) else copy(autoplay = enabled)
        }
        state.idleTurns.remove(seat.seat)
        publish(emptyList())
    }

    private fun setMic(userId: String, on: Boolean) {
        val seat = state.seatOf(userId)
        when {
            seat != null -> state.update(seat.seat) { copy(micOn = on) }
            userId in state.spectators -> state.spectators[userId] = state.spectators.getValue(userId).copy(micOn = on)
            else -> throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)
        }
        publish(emptyList())
    }

    private fun disconnected(userId: String) {
        val seat = state.seatOf(userId)
        if (seat != null) {
            state.update(seat.seat) { copy(connected = false, disconnectedAt = deps.clock.nowMillis()) }
        } else {
            state.spectators.remove(userId)
        }
        publish(emptyList())
    }

    private fun ownSeat(userId: String): MatchSeatState =
        state.seatOf(userId) ?: throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_IN_MATCH)

    /** The turn is due: the server plays it (bot, away, Autoplay, or the owner's timer ran out). */
    private fun playDueTurn() {
        if (state.ended || state.onHold || state.session.waitsForTick) return
        val session = state.session
        val owner = state.seat(session.currentSeat)
        val mode = if (owner.playedByServer) {
            BotMode.FULL
        } else {
            val deadline = state.deadline ?: return
            if (deps.clock.nowMillis() < deadline) return scheduleWake()
            state.turnTimedOut = true
            BotMode.TIMEOUT
        }
        val events = session.botPlay(owner.seat, mode) ?: session.botPlay(owner.seat, BotMode.FULL)
        if (events == null) {
            log.warn("Match $matchId: no bot move for seat ${owner.seat}")
            return
        }
        publish(events)
        scheduleWake()
    }

    private fun tick() {
        if (state.ended) return
        val now = deps.clock.nowMillis()
        state.session.tick(now, state.serverSeats())?.let { publish(it) }
        if (!state.ended) handover.tick(now)
        // Only replan when something changed, or a pending wake would keep being pushed back.
        if (rescheduleNeeded) scheduleWake()
    }

    /** Wakes the worker when the current seat must be played: after the bot's pause, or at the deadline. */
    private fun scheduleWake() {
        rescheduleNeeded = false
        val token = ++wakeToken
        if (state.ended || state.onHold || state.session.waitsForTick) return
        val owner = state.seat(state.session.currentSeat)
        val now = deps.clock.nowMillis()
        val wait = if (owner.playedByServer) {
            state.pauseMs + state.session.thinkMillis
        } else {
            (state.deadline ?: now) - now
        }
        scope.launch {
            delay(wait.coerceAtLeast(0))
            inbox.trySend(MatchCommand.Wake(token))
        }
    }

    /** Sends every viewer their snapshot; the next action waits for the animations. */
    private fun publish(events: List<GameEvent>) {
        val session = state.session
        if (session.currentSeat != state.turnOwner) closeTurn()
        val now = deps.clock.nowMillis()
        if (events.isNotEmpty()) state.pauseMs = session.pauseAfter(events)
        if (events.isNotEmpty() || state.deadline == null) {
            state.deadline = now + state.pauseMs + session.turnSeconds * BackendConstants.MILLIS_PER_SECOND
        }
        state.version++
        rescheduleNeeded = true
        seatedUsers = state.seats.filter { !it.cpu && !it.left }.map { it.userId }.toSet()
        presentUsers = state.seats.filter { !it.cpu && !it.left && it.connected }.map { it.userId }.toSet() + state.spectators.keys
        val wire = session.wireEvents(events)
        state.viewers().forEach { viewer ->
            val snapshot = snapshots.build(viewer, now, wire)
            deps.notifier.send(viewer, deps.codec.push(Channel.MATCH, MessageType.MATCH_SNAPSHOT, MatchSnapshot.serializer(), snapshot))
        }
        if (session.isFinished && !state.ended) finish(now) else if (!state.ended) checkpoints.trySend(MatchCheckpoint.of(state))
    }

    private suspend fun persist(checkpoint: MatchCheckpoint?) {
        runCatching {
            if (checkpoint == null) deps.checkpoints.delete(matchId) else deps.checkpoints.save(checkpoint, deps.clock.nowMillis())
        }.onFailure { log.warn("Match $matchId: saving the checkpoint failed", it) }
    }

    /**
     * A whole turn ended (extra rolls count once). A turn the bot played
     * on timeout with no action from the owner is idle; after 3 in a row
     * they are asked whether they are still there. Away owners collect
     * missed turns, which opens their seat.
     */
    private fun closeTurn() {
        val seat = state.turnOwner
        val owner = state.seats.firstOrNull { it.seat == seat }
        if (owner != null && owner.presence == SeatPresence.AWAY && !state.turnActed) {
            state.missedTurns[seat] = (state.missedTurns[seat] ?: 0) + 1
        }
        if (state.turnTimedOut && !state.turnActed) {
            val idle = (state.idleTurns[seat] ?: 0) + 1
            state.idleTurns[seat] = idle
            if (idle >= BackendConstants.IDLE_TURNS_BEFORE_CHECK) handover.startAbsenceCheck(seat)
        } else {
            state.idleTurns.remove(seat)
        }
        state.turnOwner = state.session.currentSeat
        state.turnTimedOut = false
        state.turnActed = false
    }

    private fun finish(now: Long) {
        state.ended = true
        state.offer = null
        ticker.cancel()
        checkpoints.trySend(null)
        checkpoints.close()
        val ranking = state.session.ranking
        val ended = MatchEnded(
            ranking.mapIndexed { index, seat ->
                RankedSeat(seat, if (state.seat(seat).removed) MatchSeatStatus.REMOVED else MatchSeatStatus.ACTIVE, index + 1)
            },
        )
        val envelope = deps.codec.push(Channel.MATCH, MessageType.MATCH_ENDED, MatchEnded.serializer(), ended)
        state.viewers().forEach { deps.notifier.send(it, envelope) }
        val record = FinishedMatch(
            matchId = matchId,
            gameId = state.setup.gameId,
            roomId = roomId,
            startedAt = state.startedAt,
            endedAt = now,
            seats = state.seats.map { s ->
                FinishedSeat(s.seat, s.userId.takeUnless { s.cpu }, ranking.indexOf(s.seat).let { if (it < 0) ranking.size + 1 else it + 1 })
            },
        )
        scope.launch {
            runCatching { deps.outcomes.ended(matchId, MatchOutcomeStatus.COMPLETED, now) }
                .onFailure { log.warn("Match $matchId: recording the outcome failed", it) }
            runCatching { deps.listener.onFinished(record) }.onFailure { log.error("Match $matchId: saving results failed", it) }
            delay(BackendConstants.FINISHED_MATCH_KEEP_MS)
            onClosed(matchId)
            stop()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MatchActor::class.java)
    }
}
