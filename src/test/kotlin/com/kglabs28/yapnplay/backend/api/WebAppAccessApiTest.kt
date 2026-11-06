package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.contract.WsTicket
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract 0.12.5: what the browser app needs (one-time WebSocket tickets, CORS for its origin only). */
class WebAppAccessApiTest {

    @Test
    fun `a ticket opens the WebSocket once`() = serverTest(config = testConfig().copy(devMode = false)) { client ->
        val token = client.newGuestToken()
        val ticket = client.post("/auth/ws-ticket") { bearerAuth(token) }.body<WsTicket>()
        assertTrue(ticket.ticket.isNotBlank())

        // Opens fine the first time (the server answers, then we close).
        client.webSocket("/ws?ticket=${ticket.ticket}") { }

        // The same ticket again is refused.
        assertFailsWith<Exception> { client.webSocket("/ws?ticket=${ticket.ticket}") { } }
    }

    @Test
    fun `a ticket needs a signed-in caller`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.post("/auth/ws-ticket").status)
    }

    @Test
    fun `the web app origin may call the API, other sites may not`() =
        serverTest(config = testConfig().copy(webAppOrigins = setOf(WEB_ORIGIN))) { client ->
            val allowed = client.options("/me") {
                header(HttpHeaders.Origin, WEB_ORIGIN)
                header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Get.value)
                header(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Authorization)
            }
            assertEquals(HttpStatusCode.OK, allowed.status)
            assertEquals(WEB_ORIGIN, allowed.headers[HttpHeaders.AccessControlAllowOrigin])

            val other = client.options("/me") {
                header(HttpHeaders.Origin, "https://evil.example.com")
                header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Get.value)
            }
            assertNull(other.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    private companion object {
        const val WEB_ORIGIN = "https://play.example.com"
    }
}
