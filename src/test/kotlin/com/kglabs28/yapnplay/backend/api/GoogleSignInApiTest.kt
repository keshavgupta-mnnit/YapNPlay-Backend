package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.GoogleSignInBody
import com.kglabs28.yapnplay.backend.contract.GoogleSignedIn
import com.kglabs28.yapnplay.backend.domain.identity.GoogleIdVerifier
import com.kglabs28.yapnplay.backend.support.serverTest
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

/** Contract 0.12.5: the web app signs in with Google (a new account first, the same one in any browser after). */
class GoogleSignInApiTest {

    /** Tokens "token-<subject>" are good for that Google account; anything else Google rejects. */
    private class FakeGoogle(override val available: Boolean = true) : GoogleIdVerifier {
        override suspend fun subjectFor(idToken: String): String? =
            idToken.removePrefix(TOKEN_PREFIX).takeIf { idToken.startsWith(TOKEN_PREFIX) }
    }

    private suspend fun HttpClient.signIn(idToken: String): HttpResponse = post("/auth/google") {
        contentType(ContentType.Application.Json)
        setBody(GoogleSignInBody(idToken))
    }

    private suspend fun HttpClient.meStatus(token: String) = get("/me") { bearerAuth(token) }.status

    private fun GoogleSignedIn.token() = "$userId.$secret"

    @Test
    fun `the first sign-in makes an account, the next brings it back`() = serverTest(google = FakeGoogle()) { client ->
        val first = client.signIn("${TOKEN_PREFIX}g1").body<GoogleSignedIn>()
        assertFalse(first.restored)
        assertEquals(HttpStatusCode.OK, client.meStatus(first.token()))

        val again = client.signIn("${TOKEN_PREFIX}g1").body<GoogleSignedIn>()
        assertTrue(again.restored)
        assertEquals(first.userId, again.userId)
        assertNotEquals(first.secret, again.secret, "a restore issues a new secret")
        assertEquals(HttpStatusCode.OK, client.meStatus(again.token()))
        assertEquals(HttpStatusCode.Unauthorized, client.meStatus(first.token()), "the old secret stops working")
    }

    @Test
    fun `two Google accounts get two accounts`() = serverTest(google = FakeGoogle()) { client ->
        val one = client.signIn("${TOKEN_PREFIX}g2").body<GoogleSignedIn>()
        val two = client.signIn("${TOKEN_PREFIX}g3").body<GoogleSignedIn>()
        assertNotEquals(one.userId, two.userId)
    }

    @Test
    fun `a rejected or blank token fails`() = serverTest(google = FakeGoogle()) { client ->
        val rejected = client.signIn("not-a-token")
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
        assertEquals(ErrorCode.NOT_AUTHENTICATED, rejected.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.BadRequest, client.signIn("   ").status)
    }

    @Test
    fun `a server without the web client id says it is unavailable`() = serverTest(google = FakeGoogle(available = false)) { client ->
        val reply = client.signIn("${TOKEN_PREFIX}g4")
        assertEquals(HttpStatusCode.ServiceUnavailable, reply.status)
        assertEquals(ErrorCode.GOOGLE_SIGN_IN_UNAVAILABLE, reply.body<ErrorBody>().code)
    }

    private companion object {
        const val TOKEN_PREFIX = "token-"
    }
}
