package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.GameCatalog
import com.kglabs28.yapnplay.backend.application.rooms.RoomPresence
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomActor
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomDeps
import com.kglabs28.yapnplay.backend.application.rooms.actor.PlayRoomState
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomAction
import com.kglabs28.yapnplay.backend.application.rooms.actor.RoomResult
import com.kglabs28.yapnplay.backend.domain.rooms.MatchStarter
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoom
import com.kglabs28.yapnplay.backend.domain.rooms.RolePolicy
import com.kglabs28.yapnplay.backend.domain.rooms.SeatPlan
import com.kglabs28.yapnplay.backend.domain.rooms.StartPolicy
import com.kglabs28.yapnplay.backend.support.InMemoryPlayRoomRepository
import com.kglabs28.yapnplay.backend.support.InMemoryFriendRepository
import com.kglabs28.yapnplay.backend.support.InMemoryJoinRequestRepository
import com.kglabs28.yapnplay.backend.support.InMemoryModerationRepository
import com.kglabs28.yapnplay.backend.application.moderation.BlockChecker
import com.kglabs28.yapnplay.backend.application.push.PushSender
import com.kglabs28.yapnplay.backend.application.push.RoomActivityNotifier
import com.kglabs28.yapnplay.backend.application.social.FriendDirectory
import com.kglabs28.yapnplay.backend.contract.JoinStatus
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomMembership
import com.kglabs28.yapnplay.backend.support.RecordingNotifier
import com.kglabs28.yapnplay.backend.support.testJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room actor on virtual time: a 10-second countdown takes no real
 * time, so every countdown case runs in milliseconds.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayRoomActorTest {
    private val repo = InMemoryPlayRoomRepository()
    private val notifier = RecordingNotifier()
    private val presence = RoomPresence()
    private val started = mutableListOf<SeatPlan>()
    private var matchCounter = 0

    private val moderation = InMemoryModerationRepository()
    private val joinRequests = InMemoryJoinRequestRepository()

    /** A room whose [members] (the leader first) already belong to it. */
    private fun TestScope.newRoom(vararg members: String = arrayOf("lead", "a", "b")): PlayRoomActor {
        val room = PlayRoom("room-1", "123456", "Test", null, "group", "lead", "lead", "lead", null, false, 0)
        val clock = Clock { testScheduler.currentTime }
        val deps = PlayRoomDeps(
            repository = repo,
            notifier = notifier,
            presence = presence,
            games = GameCatalog(GameRegistry(listOf(RoyalLudoEngine())), emptySet()),
            codec = EnvelopeCodec(testJson) { "push-${++matchCounter}" },
            startPolicy = StartPolicy(),
            rolePolicy = RolePolicy(),
            // Like the real server: the match it started is reported running (until the room hears it ended).
            matchStarter = object : MatchStarter {
                override suspend fun start(roomId: String, plan: SeatPlan): String {
                    started += plan
                    return "match-${started.size}"
                }

                override fun runningMatchIn(roomId: String): String? = started.size.takeIf { it > 0 }?.let { "match-$it" }
            },
            clock = clock,
            joinRequests = joinRequests,
            friends = FriendDirectory(InMemoryFriendRepository(), clock),
            blocks = BlockChecker(moderation, clock),
            activity = RoomActivityNotifier(PushSender { _, _ -> }, clock, backgroundScope),
            maxRoomsPerUser = 5,
            countdownSeconds = 10,
        )
        repo.rooms[room.id] = room
        repo.members[room.id] = members.associateWith { 0L }.toMutableMap()
        val memberships = members.mapIndexed { i, id -> PlayRoomMembership(room.id, id, id, i.toLong()) }
        return PlayRoomActor(PlayRoomState(room, memberships, emptyMap()), deps, backgroundScope) {}
    }

    private suspend fun PlayRoomActor.joinAll(vararg users: String) = users.forEach { ask(it, RoomAction.Join(it)) }

    private suspend fun PlayRoomActor.readyRoom(vararg players: String) {
        joinAll("lead", *players)
        ask("lead", RoomAction.SelectGame(ROYAL_LUDO_ID))
        ask("lead", RoomAction.SetBotFill(true))
        players.forEach { ask(it, RoomAction.SetReady(true)) }
    }

    private suspend fun PlayRoomActor.snapshot() = (ask("lead", RoomAction.Snapshot) as RoomResult.Snapshot).snapshot

    @Test
    fun `countdown reaches zero and seats the players`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        assertEquals(RoomResult.Done, room.ask("lead", RoomAction.StartCountdown))
        assertEquals(10_000L, room.snapshot().room.countdownDeadline)

        advanceTimeBy(10_001)
        runCurrent()

        assertEquals(1, started.size)
        assertEquals(listOf("lead", "a"), started.single().players)
        assertEquals(2, started.single().botCount)
        assertTrue(MessageType.MATCH_STARTING in notifier.typesFor("a"))
        assertNull(room.snapshot().room.countdownDeadline)
    }

    @Test
    fun `only the leader starts and only with enough ready`() = runTest {
        val room = newRoom()
        room.joinAll("lead", "a")
        room.ask("lead", RoomAction.SelectGame(ROYAL_LUDO_ID))
        assertEquals(ErrorCode.NOT_LEADER, (room.ask("a", RoomAction.StartCountdown) as RoomResult.Failed).code)
        // Leader alone counts as one ready human; two are needed.
        assertEquals(ErrorCode.NOT_ENOUGH_READY, (room.ask("lead", RoomAction.StartCountdown) as RoomResult.Failed).code)
        room.ask("a", RoomAction.SetReady(true))
        // Two ready but Ludo needs four and bot fill is off.
        assertEquals(ErrorCode.NOT_ENOUGH_READY, (room.ask("lead", RoomAction.StartCountdown) as RoomResult.Failed).code)
    }

    @Test
    fun `a ready player cancels and nothing starts`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.StartCountdown)
        assertEquals(RoomResult.Done, room.ask("a", RoomAction.CancelCountdown))
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(started.isEmpty())
    }

    @Test
    fun `a ready player leaving during the countdown stops it`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.StartCountdown)
        room.ask("a", RoomAction.Exit(force = true))
        assertNull(room.snapshot().room.countdownDeadline)
        advanceTimeBy(20_000)
        runCurrent()
        assertTrue(started.isEmpty())
    }

    @Test
    fun `start then cancel queued together gives exactly one outcome`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.StartCountdown)
        advanceTimeBy(9_999)
        room.ask("a", RoomAction.CancelCountdown)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(started.isEmpty())
        assertNull(room.snapshot().room.countdownDeadline)
    }

    @Test
    fun `switching rooms is refused while seated in a countdown`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.StartCountdown)
        val result = room.ask("a", RoomAction.Exit(force = false))
        assertEquals(ErrorCode.ALREADY_IN_ROOM, assertIs<RoomResult.Failed>(result).code)
    }

    @Test
    fun `leader going offline passes leadership to the member online longest`() = runTest {
        val room = newRoom()
        room.ask("lead", RoomAction.Join("lead"))
        advanceTimeBy(1_000)
        room.ask("a", RoomAction.Join("a"))
        advanceTimeBy(1_000)
        room.ask("b", RoomAction.Join("b"))
        room.ask("lead", RoomAction.Exit(force = true))
        assertEquals("a", room.snapshot().room.leaderId)
        assertEquals("a", repo.rooms.getValue("room-1").leaderId)
    }

    @Test
    fun `changing the game resets ready and loads default house rules`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.SelectGame(null))
        room.ask("lead", RoomAction.SelectGame(ROYAL_LUDO_ID))
        val snapshot = room.snapshot()
        assertTrue(snapshot.room.members.none { it.ready })
        assertEquals(RoyalLudoEngine().houseRuleOptions.associate { it.key to it.defaultEnabled }, snapshot.room.houseRules)
    }

    @Test
    fun `a stranger with a full room gets ROOM_FULL`() = runTest {
        val room = newRoom("lead", *Array(9) { "m$it" })
        val result = room.ask("late", RoomAction.Join("late"))
        assertEquals(ErrorCode.ROOM_FULL, assertIs<RoomResult.Failed>(result).code)
    }

    @Test
    fun `a stranger waits for the admin, who lets them in`() = runTest {
        val room = newRoom("lead")
        room.joinAll("lead")
        val pending = assertIs<RoomResult.Joined>(room.ask("new", RoomAction.Join("new"))).result
        assertEquals(JoinStatus.PENDING_APPROVAL, pending.status)
        assertEquals(listOf("new"), room.snapshot().room.waitingList.map { it.userId })
        assertEquals(RoomResult.Done, room.ask("lead", RoomAction.ApproveJoin("new")))
        assertTrue(MessageType.JOIN_APPROVED in notifier.typesFor("new"))
        val joined = assertIs<RoomResult.Joined>(room.ask("new", RoomAction.Join("new"))).result
        assertEquals(JoinStatus.JOINED, joined.status)
        assertTrue(room.snapshot().room.waitingList.isEmpty())
    }

    @Test
    fun `only the admin approves, and a blocked player cannot get ready`() = runTest {
        val room = newRoom("lead", "a")
        room.joinAll("lead", "a")
        room.ask("x", RoomAction.Join("x"))
        assertEquals(ErrorCode.NOT_ADMIN, (room.ask("a", RoomAction.ApproveJoin("x")) as RoomResult.Failed).code)
        room.ask("lead", RoomAction.SelectGame(ROYAL_LUDO_ID))
        moderation.blocked += "a"
        assertEquals(ErrorCode.BLOCKED, (room.ask("a", RoomAction.SetReady(true)) as RoomResult.Failed).code)
    }

    @Test
    fun `a started match is shown in the room until it ends`() = runTest {
        val room = newRoom()
        room.readyRoom("a")
        room.ask("lead", RoomAction.StartCountdown)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals("match-1", room.snapshot().room.matchInProgress)
        assertEquals(ErrorCode.BAD_REQUEST, (room.ask("lead", RoomAction.StartCountdown) as RoomResult.Failed).code)
        room.ask("server", RoomAction.MatchEnded("match-1"))
        assertNull(room.snapshot().room.matchInProgress)
    }

    @Test
    fun `every change raises the version`() = runTest {
        val room = newRoom()
        room.joinAll("lead")
        val before = room.snapshot().version
        room.ask("lead", RoomAction.SetVoiceState(micOn = true, speakerOn = true))
        assertEquals(before + 1, room.snapshot().version)
    }
}
