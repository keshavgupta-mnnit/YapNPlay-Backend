package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.contract.CoinsReward
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.support.newGuestToken
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
    fun `ad rewards add 500 and stop after the daily cap`() = serverTest { client ->
        val token = client.newGuestToken()
        val start = client.get("/me") { bearerAuth(token) }.body<Me>().coins
        repeat(GameSettings.AD_DAILY_CAP.default) { i ->
            val reward = client.adReward(token, "ad-$i")
            assertEquals(GameSettings.AD_REWARD.default, reward.granted)
            assertEquals(start + (i + 1) * GameSettings.AD_REWARD.default, reward.coins)
            assertEquals(GameSettings.AD_DAILY_CAP.default - i - 1, reward.adsLeftToday)
        }
        val capped = client.post("/coins/ad-reward") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(AdRewardBody("ad-new")) }
        assertEquals(HttpStatusCode.TooManyRequests, capped.status)
        assertEquals(ErrorCode.RATE_LIMITED, capped.body<ErrorBody>().code)
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
    }
}
