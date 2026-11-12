package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.application.admin.AdminUserView
import com.kglabs28.yapnplay.backend.application.weblog.WebIssueCloseBody
import com.kglabs28.yapnplay.backend.application.weblog.WebIssueDetail
import com.kglabs28.yapnplay.backend.application.weblog.WebIssuesPage
import com.kglabs28.yapnplay.backend.application.weblog.WebLogView
import com.kglabs28.yapnplay.backend.application.weblog.WebLogsRemoved
import com.kglabs28.yapnplay.backend.application.weblog.WebSymbolsUploaded
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Web reports (no sign-in, plain text) reach the admin's Web Logs, and errors group into issues like
 * Crashlytics; the players list pages with a total.
 */
class WebLogApiTest {
    private val adminToken = "test-admin-" + UUID.randomUUID()
    private val config = testConfig().copy(adminToken = adminToken)

    @Test
    fun `a browser's report is kept and listed for the admin, newest first, by kind`() = serverTest(config) { client ->
        val sent = client.send("""{"kind":"failed","stage":"page opened","message":"boom","userAgent":"iPhone","device":"cores 6","elapsedMs":1500,"version":"web-v9","stack":"Error: boom\n  at a.b\n  at c.d"}""")
        assertEquals(HttpStatusCode.NoContent, sent)
        client.send("""{"kind":"STARTED","message":"first screen after 4 s"}""")
        // Nonsense is ignored, never an error.
        assertEquals(HttpStatusCode.NoContent, client.send("not json"))

        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/web-logs").status)
        val all = client.get("/admin/web-logs") { bearerAuth(adminToken) }.body<List<WebLogView>>()
        assertEquals(listOf("STARTED", "FAILED"), all.map { it.kind })
        val failed = client.get("/admin/web-logs?kind=FAILED") { bearerAuth(adminToken) }.body<List<WebLogView>>().single()
        assertEquals("page opened", failed.stage)
        assertEquals("boom", failed.message)
        assertEquals(1500, failed.elapsedMs)
        assertEquals("web-v9", failed.version)
        // The stack keeps its lines, for the dashboard's download.
        assertEquals("Error: boom\n  at a.b\n  at c.d", failed.stack)
        assertEquals(1, client.get("/admin/web-logs?version=web-v9") { bearerAuth(adminToken) }.body<List<WebLogView>>().size)
    }

    @Test
    fun `an event keeps its context and breadcrumbs, and repeats in its page load only raise its count`() = serverTest(config) { client ->
        val event = UUID.randomUUID().toString()
        val crash = """{"kind":"CRASH","message":"RuntimeException: Can't wrap nullptr @ 1 < 2","eventId":"$event","install":"browser-1",""" +
            """"pageLoad":"load-1","version":"web-v9","userAgent":"Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) Version/27.2 Mobile Safari/604.1",""" +
            """"context":{"screen":"DoodleKingMatch","orientation":"landscape-primary"},"breadcrumbs":["+0.5s stage: running","+0.9s screen: DoodleKingMatch"]}"""
        client.send(crash)
        // The same report twice (a retried beacon) is one event.
        client.send(crash)
        client.send("""{"kind":"REPEATS","eventId":"$event","occurrences":7}""")
        // A late, smaller count never lowers it.
        client.send("""{"kind":"REPEATS","eventId":"$event","occurrences":3}""")

        val logged = client.get("/admin/web-logs?kind=CRASH") { bearerAuth(adminToken) }.body<List<WebLogView>>().single()
        assertEquals(event, logged.id)
        assertEquals(7, logged.occurrences)
        assertEquals("DoodleKingMatch", logged.context["screen"])
        assertEquals(listOf("+0.5s stage: running", "+0.9s screen: DoodleKingMatch"), logged.breadcrumbs)
        assertEquals("iOS 18.7", logged.os)
        assertEquals("Safari 27.2", logged.browser)
    }

    @Test
    fun `errors group into issues with users and crash-free sessions, and a closed issue regresses`() = serverTest(config) { client ->
        // Two browsers hit the same crash (the function trail differs by build: still one issue); one starts cleanly.
        client.send(event("CRASH", "TypeError: x is null @ 5 < 6", "a", "load-a", "web-v9"))
        client.send(event("CRASH", "TypeError: x is null @ 7 < 8", "b", "load-b", "web-v10"))
        client.send(event("NON_FATAL", "IllegalStateException: no seat [Lobby]", "a", "load-a", "web-v10"))
        listOf("load-a" to "a", "load-b" to "b", "load-c" to "c").forEach { (load, user) -> client.send(event("STARTED", "first screen", user, load, "web-v10")) }

        val page = client.get("/admin/web-issues?days=7") { bearerAuth(adminToken) }.body<WebIssuesPage>()
        assertEquals(listOf("TypeError: x is null", "IllegalStateException: no seat [Lobby]"), page.issues.map { it.title })
        val crash = page.issues.first()
        assertEquals(2, crash.events)
        assertEquals(2, crash.users)
        assertEquals(listOf("web-v10", "web-v9"), crash.versions)
        assertEquals("OPEN", crash.state)
        assertEquals(3, page.summary.sessions)
        assertEquals(2, page.summary.crashSessions)
        assertEquals(33.3, page.summary.crashFreeSessionsPct)
        assertEquals(setOf("web-v9", "web-v10"), page.versions.toSet())
        // One version only.
        assertEquals(1, client.get("/admin/web-issues?version=web-v9") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.size)

        val detail = client.get("/admin/web-issues/${crash.id}") { bearerAuth(adminToken) }.body<WebIssueDetail>()
        assertEquals(2, detail.events.size)
        assertEquals(listOf("OS", "Browser", "Device", "Screen", "Version", "Orientation"), detail.breakdowns.map { it.name })

        client.post("/admin/web-issues/${crash.id}/close") { bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(WebIssueCloseBody("fixed in web-v11")) }
        val closed = client.get("/admin/web-issues") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.single { it.id == crash.id }
        assertEquals("CLOSED", closed.state)
        assertEquals("fixed in web-v11", closed.note)
        // Closed issues sort after open ones.
        assertEquals(crash.id, client.get("/admin/web-issues") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.last().id)

        Thread.sleep(5)
        client.send(event("CRASH", "TypeError: x is null @ 9", "c", "load-c", "web-v11"))
        assertEquals("REGRESSED", client.get("/admin/web-issues") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.single { it.id == crash.id }.state)

        client.post("/admin/web-issues/${crash.id}/reopen") { bearerAuth(adminToken) }
        assertEquals("OPEN", client.get("/admin/web-issues") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.single { it.id == crash.id }.state)
        assertEquals(HttpStatusCode.NotFound, client.get("/admin/web-issues/nothing") { bearerAuth(adminToken) }.status)
    }

    @Test
    fun `a release's symbol map names its stacks at once, old reports too, and other builds stay raw`() = serverTest(config) { client ->
        // One frame a line, as JSON text (\n escapes) inside the report.
        val stack = "kotlin.createJsError@https://x/yapnplay.js:1:1\\n5375@wasm-function[5375]\\n5607@wasm-function[5607]"
        client.send("""{"kind":"CRASH","message":"Can't wrap nullptr","version":"web-v12","stack":"$stack"}""")
        client.send("""{"kind":"CRASH","message":"Can't wrap nullptr","version":"web-v11","stack":"$stack"}""")
        fun List<WebLogView>.of(version: String) = single { it.version == version }

        // No map yet: as it came, with a note.
        val before = client.get("/admin/web-logs") { bearerAuth(adminToken) }.body<List<WebLogView>>().of("web-v12")
        assertEquals("", before.decodedStack)
        assertTrue(before.decodeNote.contains("web-v12"))

        // The release uploads its map (only an admin can; a file of another build is refused).
        val map = gzip("# web-v12\n5375:org.jetbrains.skia.Shader.makeLinearGradient\n5607:com.kglabs28.yapnplay.GlassPill.sheen\n")
        assertEquals(HttpStatusCode.Unauthorized, client.put("/admin/web-symbols/web-v12") { setBody(map) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/admin/web-symbols/web-v11") { bearerAuth(adminToken); contentType(ContentType("application", "gzip")); setBody(map) }.status)
        val kept = client.put("/admin/web-symbols/web-v12") { bearerAuth(adminToken); contentType(ContentType("application", "gzip")); setBody(map) }
        assertEquals(HttpStatusCode.OK, kept.status)
        assertEquals(2, kept.body<WebSymbolsUploaded>().functions)

        // The report sent before the upload now has names; the other build's stays raw.
        val logs = client.get("/admin/web-logs") { bearerAuth(adminToken) }.body<List<WebLogView>>()
        assertTrue(logs.of("web-v12").decodedStack.contains("com.kglabs28.yapnplay.GlassPill.sheen  (wasm-function[5607])"))
        assertEquals("", logs.of("web-v11").decodedStack)
        val issue = client.get("/admin/web-issues") { bearerAuth(adminToken) }.body<WebIssuesPage>().issues.single()
        assertEquals("com.kglabs28.yapnplay.GlassPill.sheen", issue.where)
    }

    @Test
    fun `clearing removes one kind or everything`() = serverTest(config) { client ->
        listOf("CRASH", "CRASH", "SLOW").forEach { client.send("""{"kind":"$it","message":"m"}""") }
        assertEquals(1, client.delete("/admin/web-logs?kind=SLOW") { bearerAuth(adminToken) }.body<WebLogsRemoved>().removed)
        assertEquals(listOf("CRASH", "CRASH"), client.get("/admin/web-logs") { bearerAuth(adminToken) }.body<List<WebLogView>>().map { it.kind })
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/admin/web-logs").status)
        assertEquals(2, client.delete("/admin/web-logs") { bearerAuth(adminToken) }.body<WebLogsRemoved>().removed)
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

    private suspend fun HttpClient.send(body: String): HttpStatusCode =
        post("/web/log") { contentType(ContentType.Text.Plain); setBody(body) }.status

    private fun gzip(text: String): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()

    private fun event(kind: String, message: String, user: String, load: String, version: String) =
        """{"kind":"$kind","message":"$message","install":"$user","pageLoad":"$load","version":"$version","userAgent":"Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) Version/27.2 Mobile Safari/604.1"}"""
}
