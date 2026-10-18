package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.transport.CONTRACT_VERSION
import com.kglabs28.yapnplay.backend.contract.GuestCreated
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.contract.UpdateName
import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.api.http.HealthResponse
import com.kglabs28.yapnplay.backend.support.TestDatabase
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import com.kglabs28.yapnplay.backend.infrastructure.db.DbQuery
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityApiTest {

    @Test
    fun `health reports the contract version`() = serverTest { client ->
        val health = client.get("/health").body<HealthResponse>()
        assertEquals("ok", health.status)
        assertEquals(CONTRACT_VERSION, health.contractVersion)
    }

    @Test
    fun `a new guest gets an id, a secret and a Player name`() = serverTest { client ->
        val response = client.post("/auth/guest")
        assertEquals(HttpStatusCode.Created, response.status)
        val guest = response.body<GuestCreated>()
        assertTrue(guest.secret.length >= 43)
        val me = client.get("/me") { bearerAuth(guest.userId + "." + guest.secret) }.body<Me>()
        assertEquals(guest.userId, me.userId)
        assertTrue(me.displayName.matches(Regex("Player-\\d{4}")))
    }

    @Test
    fun `two guests are different people`() = serverTest { client ->
        assertNotEquals(client.newGuestToken().userIdOfToken(), client.newGuestToken().userIdOfToken())
    }

    @Test
    fun `a wrong or tampered secret gets 401 with NOT_AUTHENTICATED`() = serverTest { client ->
        val token = client.newGuestToken()
        for (bad in listOf(token + "x", token.userIdOfToken() + ".nope", "garbage", "")) {
            val response = client.get("/me") { bearerAuth(bad) }
            assertEquals(HttpStatusCode.Unauthorized, response.status, bad)
        }
        val body = client.get("/me").body<ErrorBody>()
        assertEquals(ErrorCode.NOT_AUTHENTICATED, body.code)
    }

    @Test
    fun `the database stores only the hash of the secret`() = serverTest { client ->
        val guest = client.post("/auth/guest").body<GuestCreated>()
        val stored = DbQuery(TestDatabase.handle.database).run { UsersTable.selectAll().single()[UsersTable.secretHash] }
        assertFalse(stored.contains(guest.secret))
        assertEquals(64, stored.length)
    }

    @Test
    fun `renaming trims, persists and keeps the id`() = serverTest { client ->
        val token = client.newGuestToken()
        val me = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateName("  Keshav  "))
        }.body<Me>()
        assertEquals("Keshav", me.displayName)
        assertEquals(token.userIdOfToken(), me.userId)
        assertEquals("Keshav", client.get("/me") { bearerAuth(token) }.body<Me>().displayName)
    }

    @Test
    fun `an invalid name is refused`() = serverTest { client ->
        val token = client.newGuestToken()
        val response = client.patch("/me") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateName("x".repeat(25)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ErrorCode.BAD_REQUEST, response.body<ErrorBody>().code)
    }

    @Test
    fun `guest creation is rate limited per address`() = serverTest(testConfig(guestCreationsPerHour = 2)) { client ->
        repeat(2) { assertEquals(HttpStatusCode.Created, client.post("/auth/guest").status) }
        val limited = client.post("/auth/guest")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals(ErrorCode.RATE_LIMITED, limited.body<ErrorBody>().code)
    }

}
