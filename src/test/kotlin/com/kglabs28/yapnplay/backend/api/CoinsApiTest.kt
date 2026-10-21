package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.contract.AdRewardStatus
import com.kglabs28.yapnplay.backend.contract.CoinsReward
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.support.TestDatabase
import com.kglabs28.yapnplay.backend.support.newGuestToken
import io.ktor.server.testing.ApplicationTestBuilder
import com.kglabs28.yapnplay.backend.support.serverTest
import com.kglabs28.yapnplay.backend.contract.AdRewardBody
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoinsApiTest {

    @Test
    fun `a new player starts with 1000 coins and gets the daily bonus once`() = serverTest { client ->
        val token = client.newGuestToken()
        val first = client.get("/me") { bearerAuth(token) }.body<Me>()
        assertEquals(GameSettings.DAILY_BONUS.default, first.dailyBonus)
        assertEquals(START_COINS + GameSettings.DAILY_BONUS.default, first.coins)
        assertEquals(1, first.level)
        assertEquals(0, first.levelXp)
        assertEquals(GameSettings.XP_PER_LEVEL.default, first.nextLevelXp)

        val again = client.get("/me") { bearerAuth(token) }.body<Me>()
        assertEquals(0, again.dailyBonus)
        assertEquals(first.coins, again.coins)
    }

    @Test
    fun `with no gap between ads, rewards add 500 and stop after the daily limit`() = withSettings("coins.ad_cooldown_minutes" to "0") { client ->
        val token = client.newGuestToken()
        val start = client.get("/me") { bearerAuth(token) }.body<Me>().coins
        val limit = GameSettings.AD_DAILY_LIMIT.default
        repeat(limit) { i ->
            val reward = client.adReward(token, "ad-$i")
            assertEquals(GameSettings.AD_REWARD.default, reward.granted)
            assertEquals(start + (i + 1) * GameSettings.AD_REWARD.default, reward.coins)
            assertEquals(limit - i - 1, reward.adsLeftToday)
            assertEquals(limit, reward.dailyLimit)
        }
        val capped = client.post("/coins/ad-reward") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(AdRewardBody("ad-new")) }
        assertEquals(HttpStatusCode.TooManyRequests, capped.status)
        assertEquals(ErrorCode.RATE_LIMITED, capped.body<ErrorBody>().code)
        val status = client.get("/coins/ad-status") { bearerAuth(token) }.body<AdRewardStatus>()
        assertEquals(0, status.adsLeftToday)
        val nextDay = assertNotNull(status.nextAdAt, "used up: next ad tomorrow")
        assertTrue(nextDay > status.serverTime && nextDay - status.serverTime <= DAY_MS)
    }

    @Test
    fun `one ad pays per 2 hours, and the status says when the next one can`() = serverTest { client ->
        val token = client.newGuestToken()
        val before = client.get("/coins/ad-status") { bearerAuth(token) }.body<AdRewardStatus>()
        assertEquals(GameSettings.AD_REWARD.default, before.coinsPerAd)
        assertEquals(GameSettings.AD_DAILY_LIMIT.default, before.adsLeftToday)
        assertNull(before.nextAdAt, "available now")

        val first = client.adReward(token, "first")
        val waitMs = assertNotNull(first.nextAdAt) - before.serverTime
        assertTrue(waitMs in (TWO_HOURS_MS - MINUTE_MS)..(TWO_HOURS_MS + MINUTE_MS), "next in about 2 hours ($waitMs ms)")

        val tooSoon = client.post("/coins/ad-reward") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(AdRewardBody("second")) }
        assertEquals(HttpStatusCode.TooManyRequests, tooSoon.status)
        val after = client.get("/coins/ad-status") { bearerAuth(token) }.body<AdRewardStatus>()
        assertEquals(GameSettings.AD_DAILY_LIMIT.default - 1, after.adsLeftToday)
        assertEquals(first.nextAdAt, after.nextAdAt)
        assertEquals(0, client.adReward(token, "first").granted, "a replay still gets its answer, not a refusal")
    }

    /** A server whose runtime settings start with [values] (key to JSON text). */
    private fun withSettings(vararg values: Pair<String, String>, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) {
        TestDatabase.reset()
        values.forEach { (key, value) ->
            TestDatabase.execute("INSERT INTO game_settings (key, value, updated_at, updated_by) VALUES ('$key', '$value', 0, 'test')")
        }
        serverTest(resetDatabase = false, block = block)
    }

    @Test
    fun `leagues show which fees the player can afford`() = serverTest { client ->
        val token = client.newGuestToken()
        val leagues = client.get("/coins/leagues") { bearerAuth(token) }.body<Leagues>()
        assertEquals(listOf(0, 100, 500, 1_000, 5_000), leagues.leagues.map { it.entryCoins })
        assertEquals(listOf(true, true, true, true, false), leagues.leagues.map { it.canEnter })
    }

    @Test
    fun `the same ad id never pays twice`() = serverTest { client ->
        val token = client.newGuestToken()
        val first = client.adReward(token, "same")
        val again = client.adReward(token, "same")
        assertEquals(GameSettings.AD_REWARD.default, first.granted)
        assertEquals(0, again.granted)
        assertEquals(first.coins, again.coins)
        assertEquals(first.adsLeftToday, again.adsLeftToday)
    }

    private suspend fun HttpClient.adReward(token: String, adId: String): CoinsReward =
        post("/coins/ad-reward") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(AdRewardBody(adId)) }.body()

    private companion object {
        const val START_COINS = 1_000
        const val MINUTE_MS = 60_000L
        const val TWO_HOURS_MS = 120 * MINUTE_MS
        const val DAY_MS = 24 * 60 * MINUTE_MS
    }
}
