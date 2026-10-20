package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.PlayGamesSignInBody
import com.kglabs28.yapnplay.backend.contract.PlayGamesSignedIn
import com.kglabs28.yapnplay.backend.domain.identity.PlayGamesVerifier
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Contract 0.4.3: Play Games keeps the account (link on first sign-in, restore on another phone). */
class PlayGamesSignInApiTest {

    /** Codes "code-<player>" are good for that player; anything else Google rejects. */
    private class FakeGoogle(override val available: Boolean = true) : PlayGamesVerifier {
        override suspend fun playerIdFor(serverAuthCode: String): String? =
            serverAuthCode.removePrefix(CODE_PREFIX).takeIf { serverAuthCode.startsWith(CODE_PREFIX) }
    }

    private suspend fun HttpClient.signIn(code: String, bearer: String? = null): HttpResponse = post("/auth/play-games") {
        bearer?.let { bearerAuth(it) }
        contentType(ContentType.Application.Json)
        setBody(PlayGamesSignInBody(code))
    }

    private suspend fun HttpClient.meStatus(token: String) = get("/me") { bearerAuth(token) }.status

    private fun PlayGamesSignedIn.token() = "$userId.$secret"

    @Test
    fun `the first sign-in links the guest, another phone gets that account back`() = serverTest(playGames = FakeGoogle()) { client ->
        val phone1 = client.newGuestToken()
        val linked = client.signIn("${CODE_PREFIX}p1", phone1).body<PlayGamesSignedIn>()
        assertFalse(linked.restored)
        assertEquals(phone1, linked.token(), "the guest keeps its own id and secret")

        // Same player again on the same phone: nothing changes.
        assertEquals(phone1, client.signIn("${CODE_PREFIX}p1", phone1).body<PlayGamesSignedIn>().token())

        // A new phone starts as a new guest, then Play Games brings the old account back.
        val phone2 = client.newGuestToken()
        val restored = client.signIn("${CODE_PREFIX}p1", phone2).body<PlayGamesSignedIn>()
        assertTrue(restored.restored)
        assertEquals(phone1.userIdOfToken(), restored.userId)
        assertNotEquals(phone1, restored.token(), "a restore issues a new secret")
        assertEquals(HttpStatusCode.OK, client.meStatus(restored.token()))
        assertEquals(HttpStatusCode.Unauthorized, client.meStatus(phone1), "the old secret stops working")
        assertEquals(HttpStatusCode.OK, client.meStatus(phone2), "the new phone's guest is left as it is")
    }

    @Test
    fun `without a guest a new account is made and linked`() = serverTest(playGames = FakeGoogle()) { client ->
        val first = client.signIn("${CODE_PREFIX}p2").body<PlayGamesSignedIn>()
        assertFalse(first.restored)
        assertEquals(HttpStatusCode.OK, client.meStatus(first.token()))
        val again = client.signIn("${CODE_PREFIX}p2").body<PlayGamesSignedIn>()
        assertTrue(again.restored)
        assertEquals(first.userId, again.userId)
    }

    @Test
    fun `a rejected or blank code fails, and no secret means unavailable`() = serverTest(playGames = FakeGoogle()) { client ->
        val rejected = client.signIn("not-a-code", client.newGuestToken())
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
        assertEquals(ErrorCode.NOT_AUTHENTICATED, rejected.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.BadRequest, client.signIn("   ").status)
    }

    @Test
    fun `a server without the Play Games secret says it is unavailable`() = serverTest(playGames = FakeGoogle(available = false)) { client ->
        val reply = client.signIn("${CODE_PREFIX}p3")
        assertEquals(HttpStatusCode.ServiceUnavailable, reply.status)
        assertEquals(ErrorCode.PLAY_GAMES_UNAVAILABLE, reply.body<ErrorBody>().code)
    }

    private companion object {
        const val CODE_PREFIX = "code-"
    }
}
