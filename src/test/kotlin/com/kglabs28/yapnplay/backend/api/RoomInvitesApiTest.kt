package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.contract.CreateRoom
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.FriendRequests
import com.kglabs28.yapnplay.backend.contract.InviteToRoomBody
import com.kglabs28.yapnplay.backend.contract.InviteToRoomReply
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.contract.MyRooms
import com.kglabs28.yapnplay.backend.contract.Rejected
import com.kglabs28.yapnplay.backend.contract.RoomInvites
import com.kglabs28.yapnplay.backend.contract.RoomSnapshot
import com.kglabs28.yapnplay.backend.contract.RoomSummary
import com.kglabs28.yapnplay.backend.contract.SendFriendRequestBody
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.support.WsTestClient
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contract 0.4.1/0.4.2: groups are created with invitees, friends are invited, an accepted invite is membership. */
class RoomInvitesApiTest {

    private suspend fun HttpClient.befriend(a: String, b: String) {
        val code = get("/me") { bearerAuth(b) }.body<Me>().userCode
        post("/friends/requests") {
            bearerAuth(a)
            contentType(ContentType.Application.Json)
            setBody(SendFriendRequestBody(code))
        }
        val request = get("/friends/requests") { bearerAuth(b) }.body<FriendRequests>().requests.single()
        post("/friends/requests/${request.requestId}/accept") { bearerAuth(b) }
    }

    private suspend fun HttpClient.invites(token: String) = get("/rooms/invites") { bearerAuth(token) }.body<RoomInvites>().invites

    private suspend fun HttpClient.asWs(token: String, block: suspend WsTestClient.() -> Unit) =
        webSocket("/ws", request = { bearerAuth(token) }) { WsTestClient(this).block() }

    private suspend fun WsTestClient.create(name: String?, invitees: List<String>, icon: String? = null) =
        reply(send(MessageType.CREATE_ROOM, CreateRoom.serializer(), CreateRoom(name, icon, invitees)))

    @Test
    fun `a friend invited at creation joins at once by accepting`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        client.befriend(asha, ravi)
        var roomId = ""
        client.asWs(asha) {
            val snapshot = decode(create("Hostel Night", listOf(ravi.userIdOfToken()), icon = "dice"), RoomSnapshot.serializer())
            roomId = snapshot.room.id
        }
        val invite = client.invites(ravi).single()
        assertEquals(roomId, invite.roomId)
        assertEquals("Hostel Night", invite.roomName)
        assertEquals("dice", invite.roomIcon)
        assertEquals(asha.userIdOfToken(), invite.invitedBy.userId)

        val joined = client.post("/rooms/invites/${invite.inviteId}/accept") { bearerAuth(ravi) }.body<RoomSummary>()
        assertEquals(roomId, joined.roomId)
        assertEquals(2, joined.memberCount)
        assertTrue(client.get("/rooms/mine") { bearerAuth(ravi) }.body<MyRooms>().rooms.any { it.roomId == roomId })
        assertTrue(client.invites(ravi).isEmpty(), "an answered invite is gone")
    }

    @Test
    fun `only friends are invited, and a declined invite disappears`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        val stranger = client.newGuestToken()
        client.befriend(asha, ravi)
        var roomId = ""
        client.asWs(asha) { roomId = decode(create("Crew", listOf(ravi.userIdOfToken())), RoomSnapshot.serializer()).room.id }

        val reply = client.post("/rooms/$roomId/invites") {
            bearerAuth(asha)
            contentType(ContentType.Application.Json)
            setBody(InviteToRoomBody(listOf(stranger.userIdOfToken(), ravi.userIdOfToken())))
        }.body<InviteToRoomReply>()
        assertEquals(emptyList(), reply.invited, "ravi is already invited, the stranger is no friend")
        assertEquals(listOf(stranger.userIdOfToken(), ravi.userIdOfToken()), reply.skipped)
        assertTrue(client.invites(stranger).isEmpty())

        val notMember = client.post("/rooms/$roomId/invites") {
            bearerAuth(stranger)
            contentType(ContentType.Application.Json)
            setBody(InviteToRoomBody(listOf(asha.userIdOfToken())))
        }
        assertTrue(notMember.status != HttpStatusCode.OK)

        val invite = client.invites(ravi).single()
        assertEquals(HttpStatusCode.NoContent, client.post("/rooms/invites/${invite.inviteId}/decline") { bearerAuth(ravi) }.status)
        assertTrue(client.invites(ravi).isEmpty())
    }

    @Test
    fun `a group needs a name and an invitee, and the same group twice is refused`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        client.befriend(asha, ravi)
        client.asWs(asha) {
            fun code(reply: com.kglabs28.yapnplay.backend.contract.transport.Envelope) =
                decode(reply.also { assertEquals(MessageType.REJECTED, it.type) }, Rejected.serializer())
            assertEquals(ErrorCode.BAD_REQUEST, code(create("  ", listOf(ravi.userIdOfToken()))).code)
            assertEquals(ErrorCode.BAD_REQUEST, code(create("Crew", emptyList())).code)
            assertEquals(MessageType.ROOM_SNAPSHOT, create("Crew", listOf(ravi.userIdOfToken())).type)
            val again = code(create("Another name", listOf(ravi.userIdOfToken())))
            assertEquals(ErrorCode.GROUP_EXISTS, again.code)
            assertEquals("Crew", again.message)
        }
    }
}
