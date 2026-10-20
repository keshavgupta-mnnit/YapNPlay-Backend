package com.kglabs28.yapnplay.backend.api

import com.kglabs28.yapnplay.backend.api.errors.ErrorBody
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.CoinsReward
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.support.newGuestToken
import com.kglabs28.yapnplay.backend.support.serverTest
import io.ktor.client.call.body
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
        assertEquals(BackendConstants.DAILY_BONUS_COINS, first.dailyBonus)
        assertEquals(START_COINS + BackendConstants.DAILY_BONUS_COINS, first.coins)
        assertEquals(1, first.level)
        assertEquals(0, first.levelXp)
        assertEquals(BackendConstants.XP_PER_LEVEL, first.nextLevelXp)

        val again = client.get("/me") { bearerAuth(token) }.body<Me>()
        assertEquals(0, again.dailyBonus)
        assertEquals(first.coins, again.coins)
    }

    @Test
    fun `ad rewards add 500 and stop after the daily cap`() = serverTest { client ->
        val token = client.newGuestToken()
        val start = client.get("/me") { bearerAuth(token) }.body<Me>().coins
        repeat(BackendConstants.AD_REWARDS_PER_DAY) { i ->
            val reward = client.post("/coins/ad-reward") { bearerAuth(token) }.body<CoinsReward>()
            assertEquals(BackendConstants.AD_REWARD_COINS, reward.granted)
            assertEquals(start + (i + 1) * BackendConstants.AD_REWARD_COINS, reward.coins)
            assertEquals(BackendConstants.AD_REWARDS_PER_DAY - i - 1, reward.adsLeftToday)
        }
        val capped = client.post("/coins/ad-reward") { bearerAuth(token) }
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

    private companion object {
        const val START_COINS = 1_000
    }
}
