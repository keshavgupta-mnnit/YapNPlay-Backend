package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.application.admin.AccountType
import com.kglabs28.yapnplay.backend.application.admin.AccountTypeCount
import com.kglabs28.yapnplay.backend.application.admin.AdminStats
import com.kglabs28.yapnplay.backend.application.admin.ServerUptime
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.JoinQueue
import com.kglabs28.yapnplay.backend.contract.transport.Channel
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.games.royalludo.utils.ROYAL_LUDO_ID
import com.kglabs28.yapnplay.backend.support.WsTestClient
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import com.kglabs28.yapnplay.backend.support.testJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminApiTest {
    private val adminToken = "test-admin-" + UUID.randomUUID()
    private val config = testConfig().copy(adminToken = adminToken)

    private suspend fun HttpClient.stats() = get("/admin/stats") { bearerAuth(adminToken) }.body<AdminStats>()

    @Test
    fun `stats without a token get 401`() = serverTest(config) { client ->
        val response = client.get("/admin/stats")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(ErrorCode.NOT_AUTHENTICATED, response.body<ErrorBody>().code)
    }

    @Test
    fun `a wrong admin token or a user token gets 401`() = serverTest(config) { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth("$adminToken-x") }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth(client.newGuestToken()) }.status)
    }

    @Test
    fun `a password with symbols works, but only exactly`() {
        val password = "Pa55@word!#%&*"
        serverTest(testConfig().copy(adminToken = password)) { client ->
            assertEquals(HttpStatusCode.OK, client.get("/admin/stats") { bearerAuth(password) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth(password.lowercase()) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth(password.dropLast(1)) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { header("Authorization", password) }.status)
        }
    }

    @Test
    fun `with no ADMIN_TOKEN configured nobody gets in`() = serverTest(testConfig()) { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth(adminToken) }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/stats") { bearerAuth("") }.status)
    }

    @Test
    fun `the page is served without data and is never cached`() = serverTest(config) { client ->
        val response = client.get("/admin")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Text.Html))
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue("/admin/stats" in response.bodyAsText())
    }

    @Test
    fun `uptime is public and holds only the start time`() = serverTest(config) { client ->
        val response = client.get("/admin/uptime")
        assertEquals(HttpStatusCode.OK, response.status)
        val uptime = response.body<ServerUptime>()
        assertTrue(uptime.startedAt in 1..uptime.serverTime)
        assertEquals(setOf("startedAt", "serverTime"), testJson.parseToJsonElement(response.bodyAsText()).jsonObject.keys)
    }

    @Test
    fun `stats count registered users and list every game`() = serverTest(config) { client ->
        repeat(3) { client.newGuestToken() }
        val stats = client.stats()
        assertEquals(3, stats.users.total)
        assertEquals(listOf(AccountTypeCount(AccountType.GUEST, 3)), stats.users.byType)
        assertEquals(3, stats.users.newPerDay.sumOf { it.count })
        assertEquals(0, stats.rooms.total)
        assertEquals(setOf("royal-ludo", "city-bazaar", "mono", "mono-chaos"), stats.games.map { it.gameId }.toSet())
        assertEquals(0, stats.moderation.reportsFiled)
    }

    @Test
    fun `live numbers show who is online and searching Quick Play`() = serverTest(config) { client ->
        val token = client.newGuestToken()
        client.webSocket("/ws", request = { bearerAuth(token) }) {
            val ws = WsTestClient(this)
            ws.sendOn(Channel.MATCHMAKING, MessageType.JOIN_QUEUE, JoinQueue.serializer(), JoinQueue(ROYAL_LUDO_ID))
            ws.next(MessageType.QUEUE_STATUS)
            val stats = client.stats()
            assertEquals(1, stats.live.onlineNow)
            assertEquals(1, stats.live.searchingQuickPlayNow)
            assertEquals(0, stats.live.playersInMatchNow)
            val ludo = stats.games.single { it.gameId == ROYAL_LUDO_ID }
            assertEquals(1, ludo.searchingNow)
            assertEquals(1, ludo.quickPlayJoinsSinceStart)
        }
    }
}
