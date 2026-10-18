package com.kglabs28.yapnplay.backend.infrastructure

import com.kglabs28.yapnplay.backend.api.http.TestErrorResult
import com.kglabs28.yapnplay.backend.config.BackendConfigLoader
import com.kglabs28.yapnplay.backend.infrastructure.monitoring.ErrorTracking
import com.kglabs28.yapnplay.backend.infrastructure.monitoring.SentryScrubber
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.User
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sentry stays off without a DSN, and nothing secret ever reaches it. */
class ErrorTrackingTest {

    @Test
    fun `without SENTRY_DSN error tracking stays off`() {
        assertFalse(ErrorTracking.start(testConfig()))
        assertFalse(ErrorTracking.enabled)
    }

    @Test
    fun `SENTRY_DSN is read from the environment and blank means off`() {
        val set = mapOf("DEV_MODE" to "true", "SENTRY_DSN" to " https://key@o1.ingest.sentry.io/2 ")
        assertEquals("https://key@o1.ingest.sentry.io/2", BackendConfigLoader { set[it] }.load().sentryDsn)
        val blank = mapOf("DEV_MODE" to "true", "SENTRY_DSN" to "  ")
        assertNull(BackendConfigLoader { blank[it] }.load().sentryDsn)
    }

    @Test
    fun `bearer tokens and secret parameters are removed from text`() {
        val cleaned = SentryScrubber.clean("GET /ws?token=u1.s3cr3t&x=1 with Authorization: Bearer u1.s3cr3t")
        assertFalse("s3cr3t" in cleaned, cleaned)
        assertTrue("token=[removed]" in cleaned)
        assertTrue("Bearer [removed]" in cleaned)
        assertTrue("x=1" in cleaned)
    }

    @Test
    fun `events lose sign-in headers, cookies, secrets in messages and the IP`() {
        val event = SentryEvent().apply {
            request = Request().apply {
                headers = mapOf("Authorization" to "Bearer u1.s3cr3t", "User-Agent" to "okhttp")
                cookies = "session=abc"
                queryString = "token=u1.s3cr3t"
            }
            message = Message().apply { formatted = "failed for Bearer u1.s3cr3t" }
            user = User().apply { id = "u1"; ipAddress = "10.0.0.1" }
        }
        val scrubbed = SentryScrubber.scrub(event)
        assertEquals(mapOf("User-Agent" to "okhttp"), scrubbed.request?.headers)
        assertNull(scrubbed.request?.cookies)
        assertEquals("token=[removed]", scrubbed.request?.queryString)
        assertFalse("s3cr3t" in scrubbed.message?.formatted.orEmpty())
        assertEquals("u1", scrubbed.user?.id)
        assertNull(scrubbed.user?.ipAddress)

        val crumb = SentryScrubber.scrub(Breadcrumb("200 OK: GET /ws?token=u1.s3cr3t"))
        assertFalse("s3cr3t" in crumb.message.orEmpty())
    }

    @Test
    fun `the admin test-error route needs the admin token and says when Sentry is off`() {
        val adminToken = "test-admin-" + UUID.randomUUID()
        serverTest(testConfig().copy(adminToken = adminToken)) { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.post("/admin/test-error").status)
            val reply = client.post("/admin/test-error") { bearerAuth(adminToken) }
            assertEquals(HttpStatusCode.OK, reply.status)
            assertFalse(reply.body<TestErrorResult>().sentryEnabled)
        }
    }
}
