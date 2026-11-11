package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.application.admin.AdminUserView
import com.kglabs28.yapnplay.backend.application.weblog.WebLogView
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Web load reports (no sign-in, plain text) reach the admin's Web Logs; the players list pages with a total. */
class WebLogApiTest {
    private val adminToken = "test-admin-" + UUID.randomUUID()
    private val config = testConfig().copy(adminToken = adminToken)

    @Test
    fun `a browser's report is kept and listed for the admin, newest first, by kind`() = serverTest(config) { client ->
        val sent = client.post("/web/log") {
            contentType(ContentType.Text.Plain)
            setBody("""{"kind":"failed","stage":"page opened","message":"boom","userAgent":"iPhone","device":"cores 6","elapsedMs":1500,"version":"web-v9"}""")
        }
        assertEquals(HttpStatusCode.NoContent, sent.status)
        client.post("/web/log") { contentType(ContentType.Text.Plain); setBody("""{"kind":"STARTED","message":"first screen after 4 s"}""") }
        // Nonsense is ignored, never an error.
        assertEquals(HttpStatusCode.NoContent, client.post("/web/log") { contentType(ContentType.Text.Plain); setBody("not json") }.status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/web-logs").status)
        val all = client.get("/admin/web-logs") { bearerAuth(adminToken) }.body<List<WebLogView>>()
        assertEquals(listOf("STARTED", "FAILED"), all.map { it.kind })
        val failed = client.get("/admin/web-logs?kind=FAILED") { bearerAuth(adminToken) }.body<List<WebLogView>>().single()
        assertEquals("page opened", failed.stage)
        assertEquals("boom", failed.message)
        assertEquals(1500, failed.elapsedMs)
        assertEquals("web-v9", failed.version)
    }

    @Test
    fun `players come a page at a time with the total in a header`() = serverTest(config) { client ->
        repeat(3) { client.newGuestToken() }
        val response = client.get("/admin/users?size=2&page=0") { bearerAuth(adminToken) }
        val total = response.headers["X-Total-Count"]!!.toInt()
        assertTrue(total >= 3)
        assertEquals(2, response.body<List<AdminUserView>>().size)
        val second = client.get("/admin/users?size=2&page=1") { bearerAuth(adminToken) }.body<List<AdminUserView>>()
        assertTrue(second.isNotEmpty())
    }
}
