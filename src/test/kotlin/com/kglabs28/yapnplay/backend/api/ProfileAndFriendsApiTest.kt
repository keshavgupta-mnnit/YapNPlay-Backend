package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.contract.BlockUserBody
import com.kglabs28.yapnplay.backend.contract.BlockedUsers
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.FriendRequestOutcomeCode
import com.kglabs28.yapnplay.backend.contract.FriendRequests
import com.kglabs28.yapnplay.backend.contract.Friends
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.contract.SendFriendRequestBody
import com.kglabs28.yapnplay.backend.contract.SendFriendRequestReply
import com.kglabs28.yapnplay.backend.contract.UpdateProfileBody
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Contract 0.3.1: profile fields, friends by 6-character ID, requests, blocking. */
class ProfileAndFriendsApiTest {

    private suspend fun HttpClient.me(token: String) = get("/me") { bearerAuth(token) }.body<Me>()

    private suspend fun HttpClient.ask(token: String, code: String) = post("/friends/requests") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(SendFriendRequestBody(code))
    }.body<SendFriendRequestReply>().outcome

    @Test
    fun `every guest gets a unique 6-character friend ID`() = serverTest { client ->
        val a = client.me(client.newGuestToken()).userCode
        val b = client.me(client.newGuestToken()).userCode
        assertTrue(a.matches(Regex("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")), a)
        assertNotEquals(a, b)
    }

    @Test
    fun `profile changes only the fields sent and checks them`() = serverTest { client ->
        val token = client.newGuestToken()
        val updated = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileBody(username = "keshav_28", avatarId = "a03"))
        }.body<Me>()
        assertEquals("keshav_28", updated.username)
        assertEquals("a03", updated.avatarId)
        assertTrue(updated.displayName.startsWith("Player-"))
        val bad = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileBody(username = "x"))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals(ErrorCode.BAD_REQUEST, bad.body<ErrorBody>().code)
    }

    @Test
    fun `a request by ID becomes a mutual friendship when accepted`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        val raviCode = client.me(ravi).userCode
        assertEquals(FriendRequestOutcomeCode.SENT, client.ask(asha, raviCode.lowercase()))
        assertEquals(FriendRequestOutcomeCode.ALREADY_REQUESTED, client.ask(asha, raviCode))
        val request = client.get("/friends/requests") { bearerAuth(ravi) }.body<FriendRequests>().requests.single()
        assertEquals(asha.userIdOfToken(), request.from.userId)
        client.post("/friends/requests/${request.requestId}/accept") { bearerAuth(ravi) }
        assertEquals(ravi.userIdOfToken(), client.get("/friends") { bearerAuth(asha) }.body<Friends>().friends.single().userId)
        assertEquals(asha.userIdOfToken(), client.get("/friends") { bearerAuth(ravi) }.body<Friends>().friends.single().userId)
        assertEquals(FriendRequestOutcomeCode.ALREADY_FRIENDS, client.ask(asha, raviCode))
        client.delete("/friends/${ravi.userIdOfToken()}") { bearerAuth(asha) }
        assertTrue(client.get("/friends") { bearerAuth(ravi) }.body<Friends>().friends.isEmpty())
    }

    @Test
    fun `asking someone who already asked you makes you friends at once`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        client.ask(asha, client.me(ravi).userCode)
        assertEquals(FriendRequestOutcomeCode.ACCEPTED, client.ask(ravi, client.me(asha).userCode))
        assertEquals(1, client.get("/friends") { bearerAuth(asha) }.body<Friends>().friends.size)
    }

    @Test
    fun `own ID, unknown ID and blocking give the right outcomes`() = serverTest { client ->
        val asha = client.newGuestToken()
        val ravi = client.newGuestToken()
        assertEquals(FriendRequestOutcomeCode.SELF, client.ask(asha, client.me(asha).userCode))
        assertEquals(FriendRequestOutcomeCode.NOT_FOUND, client.ask(asha, "ZZZZZZ"))
        client.post("/blocks") {
            bearerAuth(asha)
            contentType(ContentType.Application.Json)
            setBody(BlockUserBody(ravi.userIdOfToken()))
        }
        assertEquals(FriendRequestOutcomeCode.BLOCKED, client.ask(asha, client.me(ravi).userCode))
        // Someone who blocked you does not exist for you.
        assertEquals(FriendRequestOutcomeCode.NOT_FOUND, client.ask(ravi, client.me(asha).userCode))
        val blocked = client.get("/blocks") { bearerAuth(asha) }.body<BlockedUsers>().users
        assertEquals(ravi.userIdOfToken(), blocked.single().userId)
        client.delete("/blocks/${ravi.userIdOfToken()}") { bearerAuth(asha) }
        assertTrue(client.get("/blocks") { bearerAuth(asha) }.body<BlockedUsers>().users.isEmpty())
        assertEquals(FriendRequestOutcomeCode.SENT, client.ask(asha, client.me(ravi).userCode))
    }
}
