package com.kglabs28.yapnplay.backend.application.rooms.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel as Inbox
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * One live room. An actor is a single worker with an inbox: requests
 * from many users queue up and are handled strictly one at a time, so
 * two things can never happen "at the same instant" (start vs cancel,
 * two joins for the last seat). After every change the version goes up
 * and every present member gets the full [RoomSnapshot].
 */
class PlayRoomActor(
    private val state: PlayRoomState,
    private val deps: PlayRoomDeps,
    private val scope: CoroutineScope,
    /** Called once when the last member left and the room was deleted. */
    private val onDeleted: (roomId: String) -> Unit,
) {
    val roomId: String = state.room.id

    /** The current leader, readable from outside the actor (match seat handover asks). */
    @Volatile
    var leaderId: String = state.room.leaderId
        private set

    private val inbox = Inbox<RoomCommand>(Inbox.UNLIMITED)
    private val guards = RoomGuards(state, deps)
    private val countdown = CountdownHandler(state, deps, guards, ::scheduleElapsed)
    private val membership = MembershipHandler(state, deps, guards, countdown)
    private val lobby = LobbyHandler(state, deps, guards, countdown)
    private val worker: Job = scope.launch { for (command in inbox) process(command) }

    /** Sends [action] for [userId] and waits for the answer. */
    suspend fun ask(userId: String, action: RoomAction): RoomResult {
        val reply = CompletableDeferred<RoomResult>()
        return try {
            inbox.send(RoomCommand.Request(userId, action, reply))
            reply.await()
        } catch (e: ClosedSendChannelException) {
            RoomResult.Failed(ErrorCode.NOT_FOUND, BackendStrings.ROOM_NOT_FOUND)
        }
    }

    fun stop() {
        inbox.close()
        worker.cancel()
    }

    private suspend fun process(command: RoomCommand) {
        when (command) {
            is RoomCommand.Request -> command.reply.complete(handle(command.userId, command.action))
            is RoomCommand.CountdownElapsed -> {
                countdown.elapsed(command.deadline)
                broadcast()
            }
        }
    }

    private suspend fun handle(userId: String, action: RoomAction): RoomResult = try {
        var deleted = false
        val result: RoomResult = when (action) {
            is RoomAction.Join -> RoomResult.Joined(membership.join(userId, action.displayName))
            is RoomAction.Exit -> done { membership.exit(userId, action.force) }
            RoomAction.Leave -> done { deleted = membership.leave(userId) }
            is RoomAction.RemoveMember -> done { deleted = membership.remove(userId, action.userId) }
            is RoomAction.Rename -> done { membership.rename(userId, action.displayName) }
            is RoomAction.SelectGame -> done { lobby.selectGame(userId, action.gameId) }
            is RoomAction.SetBotFill -> done { lobby.setBotFill(userId, action.enabled) }
            is RoomAction.SetHouseRule -> done { lobby.setHouseRule(userId, action.key, action.enabled) }
            is RoomAction.SetReady -> done { lobby.setReady(userId, action.ready) }
            is RoomAction.SetWatching -> done { lobby.setWatching(userId, action.watching) }
            is RoomAction.SetVoiceState -> done { lobby.setVoiceState(userId, action.micOn, action.speakerOn) }
            is RoomAction.TransferLeader -> done { lobby.transferLeader(userId, action.userId) }
            is RoomAction.TransferAdmin -> done { lobby.transferAdmin(userId, action.userId) }
            RoomAction.StartCountdown -> done { countdown.start(userId) }
            RoomAction.CancelCountdown -> done { countdown.cancelBy(userId) }
            RoomAction.Snapshot -> {
                state.member(userId)
                RoomResult.Snapshot(state.snapshot(deps.clock.nowMillis(), userId, deps.friends.friendsOf(userId)))
            }
            is RoomAction.ApproveJoin -> done { membership.approve(userId, action.userId) }
            is RoomAction.DeclineJoin -> done { membership.decline(userId, action.userId) }
            is RoomAction.MatchEnded -> done { if (state.matchInProgress == action.matchId) state.matchInProgress = null }
            RoomAction.Resend -> {
                if (state.members[userId]?.online == true) sendSnapshot(userId)
                RoomResult.Done
            }
        }
        leaderId = state.room.leaderId
        if (deleted) {
            onDeleted(roomId)
            stop()
        } else if (action != RoomAction.Snapshot && action != RoomAction.Resend) {
            broadcast()
        }
        result
    } catch (e: ServiceException) {
        RoomResult.Failed(e.code, e.message)
    } catch (e: Exception) {
        log.error("Room $roomId failed on ${action::class.simpleName}", e)
        RoomResult.Failed(ErrorCode.BAD_REQUEST, BackendStrings.INTERNAL_ERROR)
    }

    private inline fun done(block: () -> Unit): RoomResult {
        block()
        return RoomResult.Done
    }

    private suspend fun broadcast() {
        state.version++
        (state.onlineIds() + state.extraRecipients).distinct().forEach { sendSnapshot(it) }
        state.extraRecipients.clear()
    }

    private suspend fun sendSnapshot(userId: String) {
        val snapshot = state.snapshot(deps.clock.nowMillis(), userId, deps.friends.friendsOf(userId))
        deps.notifier.send(userId, deps.codec.push(Channel.ROOM, MessageType.ROOM_SNAPSHOT, RoomSnapshot.serializer(), snapshot))
    }

    private fun scheduleElapsed(deadline: Long, delayMillis: Long): Job = scope.launch {
        delay(delayMillis)
        inbox.trySend(RoomCommand.CountdownElapsed(deadline))
    }

    private companion object {
        val log = LoggerFactory.getLogger(PlayRoomActor::class.java)
    }
}
