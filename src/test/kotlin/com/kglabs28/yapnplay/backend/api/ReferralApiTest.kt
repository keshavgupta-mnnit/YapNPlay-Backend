package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.contract.ClaimReferralBody
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.contract.ReferralClaimed
import com.kglabs28.yapnplay.backend.support.TestDatabase
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.support.userIdOfToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferralApiTest {

    @Test
    fun `a new player who came through a friend's link gets 1000 coins, and so does the friend`() = serverTest { client ->
        val friend = client.newGuestToken()
        val friendBefore = client.me(friend)
        val newcomer = client.newGuestToken()
        val newcomerBefore = client.me(newcomer)

        val claim = client.claim(newcomer, friendBefore.userCode)

        assertTrue(claim.credited)
        assertEquals(1_000, claim.bonus)
        assertEquals(newcomerBefore.coins + 1_000, claim.coins)
        assertEquals(newcomerBefore.coins + 1_000, client.me(newcomer).coins)
        assertEquals(friendBefore.coins + 1_000, client.me(friend).coins)
    }

    @Test
    fun `the bonus is paid once per new player, even through another friend`() = serverTest { client ->
        val friend = client.newGuestToken()
        val otherFriend = client.newGuestToken()
        val newcomer = client.newGuestToken()
        assertTrue(client.claim(newcomer, client.me(friend).userCode).credited)

        val again = client.claim(newcomer, client.me(friend).userCode)
        val viaOther = client.claim(newcomer, client.me(otherFriend).userCode)

        assertFalse(again.credited)
        assertEquals(0, again.bonus)
        assertFalse(viaOther.credited)
    }

    @Test
    fun `no bonus for an old account, an account older than the friend's, its own code or an unknown one`() = serverTest { client ->
        val friend = client.newGuestToken()
        val oldPlayer = client.newGuestToken()
        TestDatabase.execute("UPDATE users SET created_at = created_at - ${2 * 24 * 60 * 60 * 1000L} WHERE id = '${oldPlayer.userIdOfToken()}'")
        val friendCode = client.me(friend).userCode

        assertFalse(client.claim(oldPlayer, friendCode).credited)
        val newcomer = client.newGuestToken()
        assertFalse(client.claim(friend, client.me(newcomer).userCode).credited)
        assertFalse(client.claim(newcomer, client.me(newcomer).userCode).credited)
        assertFalse(client.claim(newcomer, "ZZZZZZ").credited)
        assertTrue(client.claim(newcomer, friendCode.lowercase()).credited)
    }

    @Test
    fun `no bonus for a second account made on the same phone`() = serverTest { client ->
        val friend = client.newGuestToken()
        val newcomer = client.newGuestToken()
        val phone = "test-phone-0001"
        TestDatabase.execute("UPDATE users SET device_id = '$phone' WHERE id IN ('${friend.userIdOfToken()}', '${newcomer.userIdOfToken()}')")

        assertFalse(client.claim(newcomer, client.me(friend).userCode).credited)
    }

    @Test
    fun `the admin can turn the bonus off`() = withBonus(0) { client ->
        val friend = client.newGuestToken()
        val newcomer = client.newGuestToken()

        assertFalse(client.claim(newcomer, client.me(friend).userCode).credited)
    }

    @Test
    fun `an invite link carries the invite and who shared it to the app, the Play Store and the web app`() = serverTest { client ->
        val page = client.get("/open/join/123456?ref=K7Q2M9")
        val html = page.bodyAsText()

        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue("intent://join/123456?ref=K7Q2M9#Intent;scheme=yapnplay;" in html)
        assertTrue("details?id=com.kglabs28.yapnplay&referrer=open%3Djoin%2F123456%26ref%3DK7Q2M9" in html)
        assertTrue("https://www.yapnplay.in/?open=join/123456&ref=K7Q2M9" in html)
    }

    @Test
    fun `a malformed ref is dropped and the invite still works`() = serverTest { client ->
        val html = client.get("/open/friend/AB12CD?ref=%3Cscript%3E").bodyAsText()

        assertFalse("<script>" in html.substringBefore("<script>location"))
        assertTrue("intent://friend/AB12CD#Intent;" in html)
        assertTrue("https://www.yapnplay.in/?open=friend/AB12CD\"" in html)
    }

    /** A server whose `coins.referral_bonus` setting starts at [coins]. */
    private fun withBonus(coins: Int, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        TestDatabase.reset()
        TestDatabase.execute("INSERT INTO game_settings (key, value, updated_at, updated_by) VALUES ('coins.referral_bonus', '$coins', 0, 'test')")
        serverTest(resetDatabase = false, block = block)
    }

    private suspend fun HttpClient.me(token: String): Me = get("/me") { bearerAuth(token) }.body()

    private suspend fun HttpClient.claim(token: String, code: String): ReferralClaimed =
        post("/referrals/claim") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(ClaimReferralBody(code))
        }.body()
}
