package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.contract.CreateRoom
import com.kglabs28.yapnplay.backend.contract.ApproveJoin
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.ExitLobby
import com.kglabs28.yapnplay.backend.contract.JoinRoom
import com.kglabs28.yapnplay.backend.contract.JoinRoomResult
import com.kglabs28.yapnplay.backend.contract.JoinStatus
import com.kglabs28.yapnplay.backend.contract.MatchStarting
import com.kglabs28.yapnplay.backend.contract.MatchRequestSnapshot
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot
import com.kglabs28.yapnplay.backend.contract.ViewerRole
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.MyRooms
import com.kglabs28.yapnplay.backend.contract.Presence
import com.kglabs28.yapnplay.backend.contract.Rejected
import com.kglabs28.yapnplay.backend.contract.RoomPreview
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.contract.SelectGame
import com.kglabs28.yapnplay.backend.contract.SetBotFill
import com.kglabs28.yapnplay.backend.contract.SetReady
import com.kglabs28.yapnplay.backend.contract.StartCountdown
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.support.WsTestClient
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.websocket.CloseReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomsWebSocketTest {

    private suspend fun HttpClient.connect(token: String, block: suspend WsTestClient.() -> Unit) =
        webSocket("/ws", request = { bearerAuth(token) }) { WsTestClient(this).block() }

    private suspend fun WsTestClient.createRoom(name: String? = "College Crew"): RoomSnapshot {
        val id = send(MessageType.CREATE_ROOM, CreateRoom.serializer(), CreateRoom(name))
        return decode(reply(id), RoomSnapshot.serializer())
    }

    private suspend fun WsTestClient.join(code: String): Pair<String, String> {
        val id = send(MessageType.JOIN_ROOM, JoinRoom.serializer(), JoinRoom(code))
        val reply = reply(id)
        return reply.type to reply.payload.toString()
    }

    /** [host] (admin) lets [friendId] in after their request; then the friend enters. */
    private suspend fun WsTestClient.askAndGetApproved(host: WsTestClient, code: String, friendId: String): JoinRoomResult {
        val ask = decode(reply(send(MessageType.JOIN_ROOM, JoinRoom.serializer(), JoinRoom(code))), JoinRoomResult.serializer())
        assertEquals(JoinStatus.PENDING_APPROVAL, ask.status)
        assertEquals(MessageType.ACK, host.reply(host.send(MessageType.APPROVE_JOIN, ApproveJoin.serializer(), ApproveJoin(friendId))).type)
        next(MessageType.JOIN_APPROVED)
        return decode(reply(send(MessageType.JOIN_ROOM, JoinRoom.serializer(), JoinRoom(code))), JoinRoomResult.serializer())
    }

    private suspend fun WsTestClient.rejectedCode(requestId: String): ErrorCode =
        decode(reply(requestId).also { assertEquals(MessageType.REJECTED, it.type) }, Rejected.serializer()).code

    @Test
    fun `creating a room makes you member, leader and admin with a 6-digit code`() = serverTest { client ->
        val token = client.newGuestToken()
        client.connect(token) {
            val room = createRoom().room
            assertTrue(room.code.matches(Regex("\\d{6}")))
            assertEquals(token.userIdOfToken(), room.leaderId)
            assertEquals(token.userIdOfToken(), room.adminId)
            assertEquals(Presence.ONLINE, room.members.single().presence)
        }
        val mine = client.get("/rooms/mine") { bearerAuth(token) }.body<MyRooms>()
        assertEquals("College Crew", mine.rooms.single().name)
        assertTrue(mine.rooms.single().isLeader)
    }

    @Test
    fun `a friend asks to join, the admin approves, everyone gets the new snapshot`() = serverTest { client ->
        val host = client.newGuestToken()
        val friend = client.newGuestToken()
        client.connect(host) {
            val hostWs = this
            val code = createRoom().room.code
            val preview = client.get("/rooms/preview/$code") { bearerAuth(friend) }.body<RoomPreview>()
            assertEquals(false, preview.isMember)
            client.connect(friend) {
                val result = askAndGetApproved(hostWs, code, friend.userIdOfToken())
                assertEquals(JoinStatus.JOINED, result.status)
                assertEquals(2, result.room?.memberCount)
            }
            val update = next { e -> e.type == MessageType.ROOM_SNAPSHOT && decode(e, RoomSnapshot.serializer()).room.members.size == 2 }
            assertEquals(2, decode(update, RoomSnapshot.serializer()).room.members.size)
        }
    }

    @Test
    fun `bad and unknown codes are refused`() = serverTest { client ->
        client.connect(client.newGuestToken()) {
            assertEquals(ErrorCode.INVALID_CODE, rejectedCode(send(MessageType.JOIN_ROOM, JoinRoom.serializer(), JoinRoom("12ab56"))))
            assertEquals(ErrorCode.NOT_FOUND, rejectedCode(send(MessageType.JOIN_ROOM, JoinRoom.serializer(), JoinRoom("000000"))))
        }
    }

    @Test
    fun `joining twice is harmless`() = serverTest { client ->
        client.connect(client.newGuestToken()) {
            val code = createRoom().room.code
            assertEquals(BackendTypes.JOIN_RESULT, join(code).first)
            assertEquals(BackendTypes.JOIN_RESULT, join(code).first)
        }
    }

    @Test
    fun `the room limit per user is enforced`() = serverTest(testConfig(maxRoomsPerUser = 2)) { client ->
        client.connect(client.newGuestToken()) {
            createRoom()
            createRoom()
            val id = send(MessageType.CREATE_ROOM, CreateRoom.serializer(), CreateRoom("Third"))
            assertEquals(ErrorCode.ROOM_LIMIT_REACHED, rejectedCode(id))
        }
    }

    @Test
    fun `closing the connection makes you offline but keeps membership`() = serverTest { client ->
        val host = client.newGuestToken()
        val friend = client.newGuestToken()
        lateinit var code: String
        client.connect(host) {
            val hostWs = this
            code = createRoom().room.code
            client.connect(friend) { askAndGetApproved(hostWs, code, friend.userIdOfToken()) }
            val offline = next { e ->
                e.type == MessageType.ROOM_SNAPSHOT &&
                    decode(e, RoomSnapshot.serializer()).room.members.any { it.userId == friend.userIdOfToken() && it.presence == Presence.OFFLINE }
            }
            assertEquals(2, decode(offline, RoomSnapshot.serializer()).room.members.size)
        }
        val friendRooms = client.get("/rooms/mine") { bearerAuth(friend) }.body<MyRooms>()
        assertEquals(code, friendRooms.rooms.single().code)
    }

    @Test
    fun `ExitLobby keeps membership, the room shows you offline`() = serverTest { client ->
        val token = client.newGuestToken()
        client.connect(token) {
            createRoom()
            val id = send(MessageType.EXIT_LOBBY, ExitLobby.serializer(), ExitLobby)
            assertEquals(MessageType.ACK, reply(id).type)
            // Not present anywhere now: lobby messages are refused.
            assertEquals(ErrorCode.NOT_MEMBER, rejectedCode(send(MessageType.SET_READY, SetReady.serializer(), SetReady(true))))
        }
        assertEquals(1, client.get("/rooms/mine") { bearerAuth(token) }.body<MyRooms>().rooms.size)
    }

    @Test
    fun `full room flow - pick a game, ready, countdown, match starting`() = serverTest { client ->
        val host = client.newGuestToken()
        val friend = client.newGuestToken()
        client.connect(host) {
            val hostWs = this
            val code = createRoom().room.code
            client.connect(friend) {
                askAndGetApproved(hostWs, code, friend.userIdOfToken())
                reply(send(MessageType.SET_READY, SetReady.serializer(), SetReady(true))).also {
                    // No game yet: ready is refused.
                    assertEquals(MessageType.REJECTED, it.type)
                }
                hostWs.reply(hostWs.send(MessageType.SELECT_GAME, SelectGame.serializer(), SelectGame(ROYAL_LUDO_ID)))
                hostWs.reply(hostWs.send(MessageType.SET_BOT_FILL, SetBotFill.serializer(), SetBotFill(true)))
                assertEquals(MessageType.ACK, reply(send(MessageType.SET_READY, SetReady.serializer(), SetReady(true))).type)
                val start = hostWs.send(MessageType.START_COUNTDOWN, StartCountdown.serializer(), StartCountdown)
                assertEquals(MessageType.ACK, hostWs.reply(start).type)
                val starting = decode(next(MessageType.MATCH_STARTING), MatchStarting.serializer())
                assertTrue(starting.matchId.isNotBlank())
                // The match is live on the server: the player gets a seat in its snapshot.
                val request = MatchRequestSnapshot(starting.matchId, 0)
                val snapshotReply = reply(sendOn(Channel.MATCH, MessageType.MATCH_REQUEST_SNAPSHOT, MatchRequestSnapshot.serializer(), request))
                val snapshot = decode(snapshotReply, MatchSnapshot.serializer())
                assertEquals(ViewerRole.PLAYER, snapshot.viewer.role)
                assertEquals(4, snapshot.seats.size)
                assertEquals(2, snapshot.seats.count { it.cpu })
            }
        }
    }

    @Test
    fun `malformed, oversized and unknown messages are rejected without dropping the connection`() = serverTest { client ->
        client.connect(client.newGuestToken()) {
            sendRaw("{not json")
            assertEquals(MessageType.REJECTED, next(MessageType.REJECTED).type)
            sendRaw("x".repeat(9_000))
            assertEquals(MessageType.REJECTED, next(MessageType.REJECTED).type)
            val unknown = send("Dance", ExitLobby.serializer(), ExitLobby)
            assertEquals(ErrorCode.BAD_REQUEST, rejectedCode(unknown))
            // Still alive:
            assertTrue(createRoom().room.code.isNotEmpty())
        }
    }

    @Test
    fun `a repeated requestId is processed once and gets the original answer`() = serverTest { client ->
        val token = client.newGuestToken()
        client.connect(token) {
            val first = reply(send(MessageType.CREATE_ROOM, CreateRoom.serializer(), CreateRoom("Once"), requestId = "req-1"))
            val again = reply(send(MessageType.CREATE_ROOM, CreateRoom.serializer(), CreateRoom("Once"), requestId = "req-1"))
            assertEquals(first.payload, again.payload)
        }
        assertEquals(1, client.get("/rooms/mine") { bearerAuth(token) }.body<MyRooms>().rooms.size)
    }

    @Test
    fun `a second connection replaces the first`() = serverTest { client ->
        val token = client.newGuestToken()
        client.webSocket("/ws", request = { bearerAuth(token) }) {
            val first = this
            client.connect(token) {
                val closed = first.closeReason.await()
                assertEquals(CloseReason(4000, "SessionReplaced"), closed)
                assertTrue(createRoom().room.code.isNotEmpty())
            }
        }
    }

    @Test
    fun `rooms and membership survive a server restart`() {
        lateinit var token: String
        lateinit var code: String
        serverTest { client ->
            token = client.newGuestToken()
            client.connect(token) { code = createRoom().room.code }
        }
        // A new server on the same database (no reset in between).
        serverTest(resetDatabase = false) { client ->
            val rooms = client.get("/rooms/mine") { bearerAuth(token) }.body<MyRooms>()
            assertEquals(code, rooms.rooms.single().code)
            assertEquals(0, rooms.rooms.single().onlineCount)
        }
    }

    private object BackendTypes {
        const val JOIN_RESULT = "JoinRoomResult"
    }
}
