package com.kglabs28.yapnplay.backend.application.matchmaking

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchService
import com.kglabs28.yapnplay.backend.application.moderation.BlockChecker
import com.kglabs28.yapnplay.backend.application.realtime.ClientNotifier
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.GameCatalog
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchFound
import com.kglabs28.yapnplay.backend.contract.QueueCancelReason
import com.kglabs28.yapnplay.backend.contract.QueueCancelled
import com.kglabs28.yapnplay.backend.contract.QueueStatus
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Quick Play (contract "MATCHMAKING channel"): one forming table per game.
 * The search window opens with the first player; the match starts early
 * when every seat has a human, otherwise at the deadline with CPU seats
 * (or is cancelled when the game does not fill with CPUs and too few came).
 */
class MatchmakingService(
    private val games: GameCatalog,
    private val matches: MatchService,
    private val blocks: BlockChecker,
    private val notifier: ClientNotifier,
    private val codec: EnvelopeCodec,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val lock = Mutex()
    private val forming = mutableMapOf<String, FormingTable>()
    /** Successful queue joins per game since the server started (admin dashboard only). */
    private val joinsByGame = ConcurrentHashMap<String, AtomicInteger>()

    suspend fun join(userId: String, gameId: String) {
        val game = games.findEnabled(gameId) ?: throw ServiceException(ErrorCode.GAME_UNAVAILABLE, BackendStrings.GAME_UNAVAILABLE)
        blocks.requireNotBlocked(userId)
        if (matches.isPlaying(userId)) throw ServiceException(ErrorCode.ALREADY_IN_QUEUE, BackendStrings.ALREADY_BUSY)
        lock.withLock {
            if (forming.values.any { userId in it.players }) throw ServiceException(ErrorCode.ALREADY_IN_QUEUE, BackendStrings.ALREADY_BUSY)
            val table = forming.getOrPut(gameId) {
                val deadline = clock.nowMillis() + game.matchmaking.searchSeconds * BackendConstants.MILLIS_PER_SECOND
                FormingTable(gameId, deadline).also { it.timer = scheduleDeadline(it) }
            }
            table.players += userId
            joinsByGame.getOrPut(gameId) { AtomicInteger() }.incrementAndGet()
            if (table.players.size >= game.matchmaking.seats) start(table, cpuCount = 0) else sendStatus(table)
        }
    }

    /** Leaves the search; ignored once the match was formed. */
    suspend fun leave(userId: String) = lock.withLock {
        val table = forming.values.firstOrNull { userId in it.players } ?: return@withLock
        table.players -= userId
        if (table.players.isEmpty()) {
            table.timer?.cancel()
            forming.remove(table.gameId)
        } else {
            sendStatus(table)
        }
    }

    /** Queue joins per game since the server started. */
    fun joinsSinceStart(): Map<String, Int> = joinsByGame.mapValues { it.value.get() }

    /** Players searching right now, per game. */
    suspend fun waitingNow(): Map<String, Int> = lock.withLock { forming.mapValues { it.value.players.size } }

    private fun scheduleDeadline(table: FormingTable): Job = scope.launch {
        delay((table.deadline - clock.nowMillis()).coerceAtLeast(0))
        lock.withLock {
            if (forming[table.gameId] === table) {
                // This coroutine is the timer: drop it first so start/cancel do not cancel themselves mid-way.
                table.timer = null
                deadlineReached(table)
            }
        }
    }

    private suspend fun deadlineReached(table: FormingTable) {
        val game = games.findEnabled(table.gameId)
        if (game == null) {
            cancel(table, QueueCancelReason.GAME_UNAVAILABLE)
            return
        }
        val humans = table.players.size
        when {
            game.matchmaking.fillWithCpu -> start(table, cpuCount = game.matchmaking.seats - humans)
            humans >= game.minPlayers -> start(table, cpuCount = 0)
            else -> cancel(table, QueueCancelReason.NOT_ENOUGH_PLAYERS)
        }
    }

    private suspend fun start(table: FormingTable, cpuCount: Int) {
        forming.remove(table.gameId)
        table.timer?.cancel()
        val players = table.players.toList()
        val matchId = matches.startQuickPlay(table.gameId, players, cpuCount)
        val found = codec.push(Channel.MATCHMAKING, MessageType.MATCH_FOUND, MatchFound.serializer(), MatchFound(matchId, table.gameId, players.size, cpuCount))
        players.forEach { notifier.send(it, found) }
    }

    private fun cancel(table: FormingTable, reason: QueueCancelReason) {
        forming.remove(table.gameId)
        table.timer?.cancel()
        val message = codec.push(Channel.MATCHMAKING, MessageType.QUEUE_CANCELLED, QueueCancelled.serializer(), QueueCancelled(table.gameId, reason))
        table.players.forEach { notifier.send(it, message) }
    }

    private fun sendStatus(table: FormingTable) {
        val seats = games.findEnabled(table.gameId)?.matchmaking?.seats ?: return
        val status = QueueStatus(table.gameId, table.players.size, seats, table.deadline, clock.nowMillis())
        val message = codec.push(Channel.MATCHMAKING, MessageType.QUEUE_STATUS, QueueStatus.serializer(), status)
        table.players.forEach { notifier.send(it, message) }
    }

    /** Players waiting for one game, in arrival order. */
    private class FormingTable(val gameId: String, val deadline: Long) {
        val players = linkedSetOf<String>()
        var timer: Job? = null
    }
}
