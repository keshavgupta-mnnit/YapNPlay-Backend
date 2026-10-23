package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
import com.kglabs28.yapnplay.backend.contract.EndVoted
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.application.economy.MatchCoins
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import com.kglabs28.yapnplay.backend.contract.ExtensionVoted
import com.kglabs28.yapnplay.backend.domain.economy.PotRules
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
    val startedAt: Long = state.startedAt

    /** The bots' recent decisions in this match, newest first (admin page). Read-only and thread-safe. */
    fun botTrace(): List<BotTraceEntry> = state.session.botTrace

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

    /** Players and spectators with their mic on (admin dashboard), as of the last update. */
    @Volatile
    var micOnUsers: Set<String> = emptySet()
        private set

    /** Spectators watching (admin dashboard), as of the last update. */
    @Volatile
    var spectatorCount: Int = 0
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
            is MatchAction.VoteExtension -> voteExtension(userId, action.keepPlaying)
            is MatchAction.ProposeEnd -> proposeEnd(userId)
            is MatchAction.VoteEnd -> voteEnd(userId, action.agree)
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
        if (seat.seat != session.currentSeat && !session.waitsForTick && !session.actsOutOfTurn(seat.seat, move)) {
            throw ServiceException(ErrorCode.NOT_YOUR_TURN, BackendStrings.NOT_YOUR_TURN)
        }
        val events = session.apply(seat.seat, move) ?: throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.ILLEGAL_MOVE)
        val outOfTurn = seat.seat != state.turnOwner
        if (!outOfTurn) state.turnActed = true
        handover.clearAbsenceCheck(seat.seat)
        // Acting means they are back at the table.
        if (seat.presence == SeatPresence.AWAY) handover.setAway(seat.seat, away = false)
        publish(events, outOfTurn)
    }

    /** A player's snapshot; a room member without a seat becomes a spectator. */
    private fun snapshotFor(userId: String, displayName: String, avatarId: String = ""): MatchSnapshot {
        val seat = state.seatOf(userId)
        if (seat != null) {
            if (!seat.connected) {
                state.update(seat.seat) { copy(connected = true, disconnectedAt = null) }
                publish(emptyList())
            }
            // Opened the table: the match starts once every human has.
            if (state.waitingFor.remove(userId) && state.waitingFor.isEmpty()) endStartHold()
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
        // Switched on during their own turn: it plays right after any animation, with no bot "thinking" first.
        if (enabled && state.session.currentSeat == seat.seat) state.botWakeAt = deps.clock.nowMillis()
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

    // ---------------- end-game vote (any game that allows an early end) ----------------

    private fun proposeEnd(userId: String) {
        val seat = ownSeat(userId)
        val session = state.session
        val now = deps.clock.nowMillis()
        // Only a person playing for themselves may ask; never while another vote, a handover or the start hold runs.
        if (seat.cpu || seat.left || seat.presence != SeatPresence.PLAYING) throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.CANT_PROPOSE_END)
        if (!session.canEndEarly || state.onHold || state.extensionVote != null) throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.CANT_END_NOW)
        val cooldown = deps.settings[GameSettings.END_VOTE_COOLDOWN_MINUTES] * MILLIS_PER_MINUTE
        state.lastEndProposal[seat.seat]?.let { last ->
            if (now - last < cooldown) throw ServiceException(ErrorCode.RATE_LIMITED, BackendStrings.END_VOTE_TOO_SOON)
        }
        state.lastEndProposal[seat.seat] = now
        val voters = session.stillIn - state.seats.filter { it.cpu || it.left }.map { it.seat }.toSet()
        state.endVote = EndVoteState(seat.seat, voters + seat.seat, now, now + deps.settings[GameSettings.END_VOTE_SECONDS] * BackendConstants.MILLIS_PER_SECOND)
        val vote = state.endVote!!
        if (vote.ends || vote.allVoted) decideEnd() else publish(emptyList())
    }

    private fun voteEnd(userId: String, agree: Boolean) {
        val vote = state.endVote ?: throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.NO_VOTE_RUNNING)
        if (!vote.vote(ownSeat(userId).seat, agree)) throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.CANT_VOTE)
        if (vote.ends || vote.allVoted || vote.cannotPass) decideEnd() else publish(emptyList())
    }

    private fun endVoteTick(now: Long) {
        val vote = state.endVote ?: return
        if (now >= vote.endsAt) decideEnd()
    }

    /** More than half said yes: the match ends now. Otherwise play resumes with the turn timer it had left. */
    private fun decideEnd() {
        val vote = state.endVote ?: return
        state.endVote = null
        val ended = vote.ends
        val events = if (ended) state.session.endEarly().orEmpty() else emptyList()
        if (!ended) state.deadline = state.deadline?.plus(deps.clock.nowMillis() - vote.startedAt)
        publish(events + EndVoteDecided(ended, vote.yes.size, vote.voters.size))
        scheduleWake()
    }

    // ---------------- keep-playing vote at the game's limit (every game) ----------------

    /** Opens the vote when the game reaches its limit, and closes it in time. */
    private fun extensionTick(now: Long) {
        val session = state.session
        val vote = state.extensionVote
        when {
            vote != null -> if (now >= vote.endsAt) decideExtension()
            session.atLimit -> {
                // Only real people still in vote: never CPUs or leavers. A human whose seat the bot is
                // holding (away, missed turns) still votes, so a lone player with CPUs always decides.
                val voters = session.stillIn - state.seats.filter { it.cpu || it.left }.map { it.seat }.toSet()
                // The clock starts after the table has shown the last turn (its messages can take seconds),
                // so players get the whole vote time with the question on screen.
                val endsAt = now + state.pauseMs + deps.settings[GameSettings.VOTE_SECONDS] * BackendConstants.MILLIS_PER_SECOND
                state.extensionVote = ExtensionVoteState(voters, endsAt)
                if (voters.isEmpty()) decideExtension() else publish(emptyList())
            }
        }
    }

    private fun voteExtension(userId: String, keepPlaying: Boolean) {
        val vote = state.extensionVote ?: throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.NO_VOTE_RUNNING)
        val seat = ownSeat(userId).seat
        if (!vote.vote(seat, keepPlaying)) throw ServiceException(ErrorCode.ILLEGAL_MOVE, BackendStrings.CANT_VOTE)
        if (vote.allVoted) decideExtension() else publish(emptyList())
    }

    /** More rounds/minutes when at least half said yes, else the match ends as at its limit. */
    private fun decideExtension() {
        val vote = state.extensionVote ?: return
        state.extensionVote = null
        val continued = vote.continues
        val events = (if (continued) state.session.extend() else state.session.endAtLimit()).orEmpty()
        publish(events + ExtensionDecided(continued, vote.yes.size, vote.voters.size))
        scheduleWake()
    }

    /** Everyone is at the table (or the wait is over): the first turn and its timer start. */
    private fun endStartHold() {
        state.startHoldUntil = null
        state.waitingFor.clear()
        state.deadline = null
        publish(emptyList())
        scheduleWake()
    }

    /** The turn is due: the server plays it (bot, away, Autoplay, or the owner's timer ran out). */
    private fun playDueTurn() {
        if (state.ended || state.onHold || state.session.waitsForTick) return
        val session = state.session
        val owner = state.seat(session.currentSeat)
        val mode = if (owner.playedByServer) {
            if (owner.autoplay && !owner.cpu && !owner.left && owner.presence == SeatPresence.PLAYING) BotMode.AUTOPLAY else BotMode.FULL
        } else {
            val deadline = state.deadline ?: return
            if (deps.clock.nowMillis() < deadline) return scheduleWake()
            state.turnTimedOut = true
            BotMode.TIMEOUT
        }
        val events = session.botPlay(owner.seat, mode) ?: session.botPlay(owner.seat, BotMode.FULL)
        if (events == null) {
            log.warn("Match $matchId: no bot move for seat ${owner.seat}")
            state.botWakeAt = null
            return
        }
        publish(events)
        scheduleWake()
    }

    private fun tick() {
        if (state.ended) return
        val now = deps.clock.nowMillis()
        val holdUntil = state.startHoldUntil
        if (holdUntil != null && now >= holdUntil) {
            // Still not here after the wait: they count as gone (soon away, the server plays for them).
            state.waitingFor.forEach { userId ->
                state.seatOf(userId)?.let { seat ->
                    state.update(seat.seat) { copy(connected = false, disconnectedAt = now - BackendConstants.DISCONNECT_GRACE_MS) }
                }
            }
            endStartHold()
        }
        // While players vote on ending the match, the game stands still (no limits, auctions or CPU bids).
        // The game's own clock (bots catching, auctions) never gives the seat to play more time.
        if (state.endVote == null) state.session.tick(now, state.serverSeats(), state.botSeats())?.let { publish(it, outOfTurn = true) }
        if (!state.ended) handover.tick(now)
        if (!state.ended) endIfNoHumans()
        if (!state.ended) extensionTick(now)
        if (!state.ended) endVoteTick(now)
        // Only replan when something changed, or a pending wake would keep being pushed back.
        if (rescheduleNeeded) scheduleWake()
    }

    /**
     * Every human has left (away or on Autoplay still counts as here; spectators
     * don't): the match ends at once, ranked as it stands, and pays as usual.
     */
    private fun endIfNoHumans() {
        if (state.seats.any { !it.cpu && !it.left && !it.removed }) return
        val events = state.session.endWithoutHumans() ?: return
        log.info("Match $matchId: every human left, ending it")
        publish(events)
    }

    /** Wakes the worker when the current seat must be played: after the bot's pause, or at the deadline. */
    private fun scheduleWake() {
        rescheduleNeeded = false
        val token = ++wakeToken
        if (state.ended || state.onHold || state.session.waitsForTick) return
        val owner = state.seat(state.session.currentSeat)
        val now = deps.clock.nowMillis()
        val wait = if (owner.playedByServer) {
            // The bot's think time counts inside its turn: never past the deadline.
            val deadlineLeft = state.deadline?.let { it - now } ?: Long.MAX_VALUE
            val wakeAt = state.botWakeAt ?: (now + state.pauseMs + state.session.thinkMillis).also { state.botWakeAt = it }
            (wakeAt - now).coerceAtMost(deadlineLeft)
        } else {
            state.botWakeAt = null
            (state.deadline ?: now) - now
        }
        scope.launch {
            delay(wait.coerceAtLeast(0))
            inbox.trySend(MatchCommand.Wake(token))
        }
    }

    /**
     * A seat the server plays thinks a while before each move, as a person
     * would (the game's think time, e.g. 3-7 s at random): picked once per
     * step, when its own move or a new turn is published. It always fits in
     * the turn's time limit: with less time left than it drew, it moves a
     * little before the deadline instead. Another seat's out-of-turn event
     * (a catch) only waits for its animation.
     */
    private fun planBotMove(now: Long, ownStep: Boolean) {
        val session = state.session
        val owner = state.seats.firstOrNull { it.seat == session.currentSeat }
        if (owner == null || !owner.playedByServer || session.isFinished) {
            state.botWakeAt = null
            return
        }
        val afterAnimation = now + state.pauseMs
        val planned = state.botWakeAt.takeUnless { ownStep }?.let { maxOf(it, afterAnimation) } ?: (afterAnimation + session.thinkMillis)
        val latest = state.deadline?.let { it - BOT_DEADLINE_MARGIN_MS } ?: planned
        state.botWakeAt = minOf(planned, latest).coerceAtLeast(afterAnimation)
    }

    /**
     * Sends every viewer their snapshot; the next action waits for the animations.
     * The turn clock restarts only for a new turn (or when the game asks, e.g.
     * after a roll). A follow-up step by the same seat keeps the time left, at
     * least the follow-up floor, plus the animation; [outOfTurn] events (another
     * seat's MONO! or Caught, the game's own clock) never give the seat more time.
     */
    private fun publish(events: List<GameEvent>, outOfTurn: Boolean = false) {
        val session = state.session
        val newTurn = session.currentSeat != state.turnOwner
        if (newTurn) closeTurn()
        val now = deps.clock.nowMillis()
        // A new turn starts only after the last one's animation and the gap between turns.
        if (events.isNotEmpty()) state.pauseMs = session.pauseAfter(events) + if (newTurn) deps.settings[GameSettings.TURN_GAP_MS] else 0
        val deadline = state.deadline
        val fullTurn = now + state.pauseMs + session.turnSeconds * BackendConstants.MILLIS_PER_SECOND
        state.deadline = when {
            deadline == null || newTurn -> fullTurn
            events.isEmpty() -> deadline
            outOfTurn -> maxOf(deadline, now + state.pauseMs)
            session.restartsClockOn(events) -> fullTurn
            else -> now + state.pauseMs + maxOf(deadline - now, deps.settings[GameSettings.TURN_FOLLOW_UP_SECONDS] * BackendConstants.MILLIS_PER_SECOND)
        }
        planBotMove(now, events.isNotEmpty() && !outOfTurn)
        state.version++
        rescheduleNeeded = true
        seatedUsers = state.seats.filter { !it.cpu && !it.left }.map { it.userId }.toSet()
        presentUsers = state.seats.filter { !it.cpu && !it.left && it.connected }.map { it.userId }.toSet() + state.spectators.keys
        micOnUsers = state.seats.filter { !it.cpu && !it.left && it.micOn }.map { it.userId }.toSet() +
            state.spectators.filterValues { it.micOn }.keys
        spectatorCount = state.spectators.size
        // The vote's result is the same for every game: added after the game's own events.
        val voteWire = events.filterIsInstance<ExtensionDecided>().map {
            json.encodeToJsonElement(ExtensionVoted.serializer(), ExtensionVoted(it.continued, it.yes, it.voters)).jsonObject
        } + events.filterIsInstance<EndVoteDecided>().map {
            json.encodeToJsonElement(EndVoted.serializer(), EndVoted(it.ended, it.yes, it.voters)).jsonObject
        }
        state.viewers().forEach { viewer ->
            val snapshot = snapshots.build(viewer, now, session.wireEventsFor(events, state.seatOf(viewer)?.seat) + voteWire)
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
        val record = FinishedMatch(
            matchId = matchId,
            gameId = state.setup.gameId,
            roomId = roomId,
            startedAt = state.startedAt,
            endedAt = now,
            seats = state.seats.map { s ->
                val place = ranking.indexOf(s.seat).let { if (it < 0) ranking.size + 1 else it + 1 }
                FinishedSeat(
                    s.seat, s.userId.takeUnless { s.cpu }, place,
                    left = s.left,
                    completed = !s.cpu && !s.left && !s.removed && s.presence != SeatPresence.BOT,
                    xp = if (s.cpu) 0 else deps.progression.xpFor(place, s.left, now - state.startedAt),
                    displayName = s.displayName,
                    avatarId = s.avatarId,
                )
            },
            entryCoins = state.setup.entryCoins,
            paid = state.setup.paid,
            potSplit = state.setup.potSplit,
        )
        val seats = ranking.map { state.seat(it) }
        scope.launch {
            // Coins are paid (one transaction) before the result goes out, so the table shows what was paid.
            val coins = runCatching { deps.economy.settle(record) }
                .onFailure { log.error("Match $matchId: paying coins failed", it) }
                .getOrDefault(MatchCoins())
            // The table shows every seat's place share and stake as if a person held it. Only paying humans
            // still at the table are credited; a CPU's or leaver's share is shown, never paid to anyone.
            val cpuCount = record.seats.count { it.userId == null }
            val completionShown = if (now - state.startedAt >= deps.settings[GameSettings.COMPLETION_MIN_MINUTES] * MILLIS_PER_MINUTE) {
                deps.settings[GameSettings.COMPLETION_COINS]
            } else {
                0
            }
            val ended = MatchEnded(
                seats.mapIndexed { index, s ->
                    val paidIn = s.cpu || s.userId in record.paid
                    RankedSeat(
                        s.seat, if (s.removed) MatchSeatStatus.REMOVED else MatchSeatStatus.ACTIVE, index + 1,
                        coinsWon = PotRules.placeShare(record.entryCoins, record.paid.size, cpuCount, record.potSplit, index + 1),
                        // CPUs show the completion coins a person would get for the same match (the daily cap aside).
                        completionCoins = if (s.cpu) completionShown else coins.completion[s.userId] ?: 0,
                        stake = if (paidIn && record.paid.isNotEmpty()) record.entryCoins else 0,
                        xpGained = record.seats.firstOrNull { it.seat == s.seat }?.xp ?: 0,
                    )
                },
            )
            val envelope = deps.codec.push(Channel.MATCH, MessageType.MATCH_ENDED, MatchEnded.serializer(), ended)
            state.viewers().forEach { deps.notifier.send(it, envelope) }
            runCatching { deps.outcomes.ended(matchId, MatchOutcomeStatus.COMPLETED, now) }
                .onFailure { log.warn("Match $matchId: recording the outcome failed", it) }
            // The stored result keeps each seat's coins as the table showed them (for the player's history).
            val shown = ended.ranking.associateBy { it.seat }
            val stored = record.copy(
                seats = record.seats.map { seat ->
                    shown[seat.seat]?.let { seat.copy(coinsWon = it.coinsWon, completionCoins = it.completionCoins, stake = it.stake) } ?: seat
                },
            )
            runCatching { deps.listener.onFinished(stored) }.onFailure { log.error("Match $matchId: saving results failed", it) }
            delay(BackendConstants.FINISHED_MATCH_KEEP_MS)
            onClosed(matchId)
            stop()
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(MatchActor::class.java)
        const val MILLIS_PER_MINUTE = 60_000L
        /** A bot moves at least this long before its turn's deadline, so the move never races it. */
        const val BOT_DEADLINE_MARGIN_MS = 500L
    }
}

/** The keep-playing vote was decided (shared by every game; sent as an ExtensionVoted event). */
private const val MILLIS_PER_MINUTE = 60_000L

data class ExtensionDecided(val continued: Boolean, val yes: Int, val voters: Int) : GameEvent

/** The end-game vote's result (the same for every game). */
data class EndVoteDecided(val ended: Boolean, val yes: Int, val voters: Int) : GameEvent

private val json = Json { encodeDefaults = true }
