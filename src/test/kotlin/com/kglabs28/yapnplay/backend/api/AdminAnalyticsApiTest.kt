package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.application.admin.AdminAnalytics
import com.kglabs28.yapnplay.backend.application.admin.AdminUserView
import com.kglabs28.yapnplay.backend.application.admin.SourceCount
import com.kglabs28.yapnplay.backend.contract.GuestCreated
import com.kglabs28.yapnplay.backend.support.TestDatabase
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminAnalyticsApiTest {
    private val adminToken = "test-admin-" + UUID.randomUUID()
    private val config = testConfig().copy(adminToken = adminToken)

    @Test
    fun `sign-ups record where they came from, and the period counts them by source`() = serverTest(config) { client ->
        client.guest(userAgent = "ktor-client")
        client.guest(userAgent = IPHONE)
        client.guest(userAgent = IPHONE)
        client.guest(userAgent = WINDOWS)

        val analytics = client.analytics()

        assertEquals(4, analytics.summary.newPlayers)
        assertEquals(
            setOf(SourceCount("web", "ios", 2), SourceCount("android", "", 1), SourceCount("web", "windows", 1)),
            analytics.sources.toSet(),
        )
        assertEquals(analytics.sources.toSet(), analytics.allTimeSources.toSet())
        assertEquals(AdminAnalytics.BUCKET_HOUR, analytics.bucket)
        assertEquals(4, analytics.timeline.sumOf { it.newPlayers })
    }

    @Test
    fun `a later sign-in elsewhere keeps where the account was made`() = serverTest(config) { client ->
        val made = client.guest(userAgent = IPHONE)
        client.post("/auth/guest") {
            header(HttpHeaders.UserAgent, WINDOWS)
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"userId":"${made.userId}","secret":"${made.secret}","restore":false}""")
        }

        assertEquals(setOf(SourceCount("web", "ios", 1)), client.analytics().sources.toSet())
    }

    @Test
    fun `a period outside the sign-ups is empty, and longer periods go per day`() = serverTest(config) { client ->
        client.guest(userAgent = IPHONE)
        val from = System.currentTimeMillis() - 30L * DAY_MS
        val to = System.currentTimeMillis() - 20L * DAY_MS

        val old = client.get("/admin/analytics?from=$from&to=$to") { bearerAuth(adminToken) }.body<AdminAnalytics>()

        assertEquals(0, old.summary.newPlayers)
        assertEquals(emptyList(), old.sources)
        assertEquals(AdminAnalytics.BUCKET_DAY, old.bucket)
        assertTrue(old.timeline.size in 10..11)
        assertTrue(old.games.isNotEmpty(), "every game is listed, even unplayed")
    }

    @Test
    fun `the players list sorts by any column, both ways, ties newest first`() = serverTest(config) { client ->
        val a = client.guest(userAgent = IPHONE).userId
        val b = client.guest(userAgent = "ktor-client").userId
        val c = client.guest(userAgent = WINDOWS).userId
        TestDatabase.execute("UPDATE users SET coins = 300 WHERE id = '$a'")
        TestDatabase.execute("UPDATE users SET coins = 100 WHERE id = '$b'")
        TestDatabase.execute("UPDATE users SET coins = 200 WHERE id = '$c'")

        assertEquals(listOf(a, c, b), client.players("coins", "desc").map { it.userId })
        assertEquals(listOf(b, c, a), client.players("coins", "asc").map { it.userId })
        assertEquals("ios", client.players("coins", "desc").first().deviceOs)
        assertEquals("android", client.players("coins", "asc").first().platform)
        assertEquals(3, client.players("games", "desc").size)
    }

    private suspend fun HttpClient.guest(userAgent: String): GuestCreated =
        post("/auth/guest") { header(HttpHeaders.UserAgent, userAgent) }.also { assertEquals(HttpStatusCode.Created, it.status) }.body()

    private suspend fun HttpClient.analytics(): AdminAnalytics = get("/admin/analytics") { bearerAuth(adminToken) }.body()

    private suspend fun HttpClient.players(sort: String, dir: String): List<AdminUserView> =
        get("/admin/users?sort=$sort&dir=$dir") { bearerAuth(adminToken) }.body()

    private companion object {
        const val IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"
        const val WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0 Safari/537.36"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
