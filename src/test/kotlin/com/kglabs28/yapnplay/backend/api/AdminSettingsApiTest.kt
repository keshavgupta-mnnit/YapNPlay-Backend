package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.api.http.AdminBlockBody
import com.kglabs28.yapnplay.backend.api.http.AdminCoinsBody
import com.kglabs28.yapnplay.backend.api.http.AdminCoinsResult
import com.kglabs28.yapnplay.backend.api.http.AdminNoteBody
import com.kglabs28.yapnplay.backend.api.http.AdminSettingBody
import com.kglabs28.yapnplay.backend.application.admin.AdminAuditView
import com.kglabs28.yapnplay.backend.application.admin.AdminUserView
import com.kglabs28.yapnplay.backend.application.settings.SettingView
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.testConfig
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The admin dashboard's runtime settings and player actions: token only, validated, audited. */
class AdminSettingsApiTest {
    private val adminToken = "test-admin-" + UUID.randomUUID()
    private val config = testConfig().copy(adminToken = adminToken)

    private suspend fun HttpClient.setSetting(key: String, value: JsonElement): HttpResponse =
        put("/admin/settings/$key") { bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminSettingBody(value, "Tester", "test")) }

    @Test
    fun `settings need the token, are validated, apply at once and are audited`() = serverTest(config) { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/settings").status)
        val list = client.get("/admin/settings") { bearerAuth(adminToken) }.body<List<SettingView>>()
        assertTrue(list.any { it.key == "coins.daily_bonus" && it.value == "100" })

        assertEquals(HttpStatusCode.BadRequest, client.setSetting("coins.daily_bonus", JsonPrimitive(-5)).status)
        assertEquals(HttpStatusCode.BadRequest, client.setSetting("coins.league_fees", JsonArray(listOf(5, 100).map(::JsonPrimitive))).status)
        assertEquals(HttpStatusCode.NotFound, client.setSetting("nope", JsonPrimitive(1)).status)

        assertEquals(HttpStatusCode.OK, client.setSetting("coins.daily_bonus", JsonPrimitive(250)).status)
        assertEquals(HttpStatusCode.OK, client.setSetting("coins.league_fees", JsonArray(listOf(0, 200, 600, 2_000, 9_000).map(::JsonPrimitive))).status)
        val token = client.newGuestToken()
        assertEquals(250, client.get("/me") { bearerAuth(token) }.body<Me>().dailyBonus)
        val leagues = client.get("/coins/leagues") { bearerAuth(token) }.body<Leagues>()
        assertEquals(listOf(0, 200, 600, 2_000, 9_000), leagues.leagues.map { it.entryCoins })

        val audit = client.get("/admin/audit") { bearerAuth(adminToken) }.body<List<AdminAuditView>>()
        val bonus = audit.first { it.target == "coins.daily_bonus" }
        assertEquals("100", bonus.oldValue)
        assertEquals("250", bonus.newValue)
        assertEquals("Tester", bonus.changedBy)
    }

    @Test
    fun `players can be found, blocked, unblocked and given coins with a reason`() = serverTest(config) { client ->
        val token = client.newGuestToken()
        val userId = token.userIdOfToken()
        val start = client.get("/me") { bearerAuth(token) }.body<Me>().coins

        val found = client.get("/admin/users?q=$userId") { bearerAuth(adminToken) }.body<List<AdminUserView>>().single()
        assertNull(found.blockedUntil)

        client.post("/admin/users/$userId/block") { bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminBlockBody(2, "Tester")) }
        assertNotNull(client.get("/admin/users?q=$userId") { bearerAuth(adminToken) }.body<List<AdminUserView>>().single().blockedUntil)
        client.post("/admin/users/$userId/unblock") { bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminNoteBody("Tester")) }
        assertNull(client.get("/admin/users?q=$userId") { bearerAuth(adminToken) }.body<List<AdminUserView>>().single().blockedUntil)

        val noReason = client.post("/admin/users/$userId/coins") { bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminCoinsBody(500, "Tester")) }
        assertEquals(HttpStatusCode.BadRequest, noReason.status)
        val given = client.post("/admin/users/$userId/coins") {
            bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminCoinsBody(500, "Tester", "lost coins in a crash"))
        }.body<AdminCoinsResult>()
        assertEquals(start + 500, given.coins)
        val tooMuch = client.post("/admin/users/$userId/coins") {
            bearerAuth(adminToken); contentType(ContentType.Application.Json); setBody(AdminCoinsBody(-1_000_000, "Tester", "test"))
        }
        assertEquals(ErrorCode.BAD_REQUEST, tooMuch.body<ErrorBody>().code)
        assertEquals(start + 500, client.get("/me") { bearerAuth(token) }.body<Me>().coins)
    }
}
