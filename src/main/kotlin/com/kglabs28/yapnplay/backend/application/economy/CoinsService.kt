package com.kglabs28.yapnplay.backend.application.economy

import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.CoinLedgerTable
import com.kglabs28.yapnplay.backend.domain.economy.PotRules
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.CoinsReward
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.League
import com.kglabs28.yapnplay.backend.contract.Leagues
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch
import java.time.Instant
import java.time.ZoneId

/**
 * Coins: play money only (never bought, never cashed out). A daily bonus,
 * rewarded ads, and match pots: every paid seat's entry fee (the bank pays
 * for CPU seats), shared by finishing place. No fee, no prize; players who
 * left get nothing.
 */
class CoinsService(
    private val store: CoinStore,
    private val clock: Clock,
    private val ads: AdVerifier = AdVerifier.AcceptAll,
    private val settings: Settings = Settings.Defaults,
) : EntryFees, MatchEconomy {

    /** Today's +100, given once a day (India time) the first time the app asks. The coins granted, or 0. */
    suspend fun dailyBonus(userId: String): Int {
        val now = clock.nowMillis()
        val bonus = settings[GameSettings.DAILY_BONUS]
        return if (bonus > 0 && store.claimDaily(userId, dayOf(now), bonus, now)) bonus else 0
    }

    /**
     * A fully watched rewarded ad: +500, a few times a day. [adId] is unique
     * per ad view, so a retry or replay pays nothing. [ads] is the hook for
     * AdMob server-side verification (SSV) later; today it accepts every view.
     */
    suspend fun adReward(userId: String, adId: String): CoinsReward {
        if (adId.isBlank() || adId.length > CoinLedgerTable.REF_MAX_LENGTH) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_AD_ID)
        if (!ads.verify(userId, adId)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_AD_ID)
        val now = clock.nowMillis()
        val claim = store.claimAdReward(userId, adId, dayOf(now), settings[GameSettings.AD_REWARD], settings[GameSettings.AD_DAILY_CAP], now)
            ?: throw ServiceException(ErrorCode.RATE_LIMITED, BackendStrings.AD_REWARDS_USED)
        return CoinsReward(coins = store.balance(userId), granted = claim.granted, adsLeftToday = claim.adsLeftToday)
    }

    suspend fun balance(userId: String): Int = store.balance(userId)

    /** The Quick Play leagues, and which ones this player can afford now. */
    suspend fun leagues(userId: String): Leagues {
        val coins = store.balance(userId)
        val fees = settings[GameSettings.LEAGUE_FEES]
        return Leagues(coins, GameSettings.LEAGUE_NAMES.zip(fees).map { (name, entry) -> League(entry, name, coins >= entry) })
    }

    override suspend fun canEnter(userId: String, entryCoins: Int): Boolean =
        entryCoins == 0 || store.balance(userId) >= entryCoins

    override suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int): List<String> {
        if (entryCoins <= 0) return emptyList()
        val now = clock.nowMillis()
        return userIds.filter { store.change(it, -entryCoins, CoinReason.ENTRY, matchId, now) != null }
    }

    /**
     * Pays a finished match: the pot by place (entry x paid seats, CPU seats
     * paid by the bank; nothing for players who did not pay or who left),
     * plus completion coins to every human still playing for themselves at
     * the end, if the match ran long enough and they are under today's cap.
     */
    override suspend fun settle(match: FinishedMatch): MatchCoins {
        // The fee, payers and split were fixed when the match opened.
        val won = PotRules.payouts(match.entryCoins, match.paid.toSet(), match.seats, match.potSplit)
        val longEnough = match.endedAt - match.startedAt >= settings[GameSettings.COMPLETION_MIN_MINUTES] * MILLIS_PER_MINUTE
        val finishers = if (longEnough) match.seats.filter { it.completed }.mapNotNull { it.userId } else emptyList()
        val now = clock.nowMillis()
        val completion = store.settle(
            match.matchId, won, finishers, settings[GameSettings.COMPLETION_COINS],
            dayOf(now), settings[GameSettings.COMPLETION_DAILY_CAP], now,
        )
        return MatchCoins(won, completion)
    }

    private fun dayOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate().toEpochDay().toInt()

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Entry fees in coins; the free version lets everyone in and charges nobody. */
interface EntryFees {
    suspend fun canEnter(userId: String, entryCoins: Int): Boolean

    /** Takes [entryCoins] from each player who has it; returns those who paid. */
    suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int): List<String>

    object Free : EntryFees {
        override suspend fun canEnter(userId: String, entryCoins: Int) = true
        override suspend fun charge(userIds: List<String>, matchId: String, entryCoins: Int) = emptyList<String>()
    }
}

/** Checks that an ad view really happened (AdMob SSV callback, later). */
fun interface AdVerifier {
    suspend fun verify(userId: String, adId: String): Boolean

    companion object {
        val AcceptAll = AdVerifier { _, _ -> true }
    }
}

/** Coins a finished match paid, by user id. */
data class MatchCoins(
    val won: Map<String, Int> = emptyMap(),
    val completion: Map<String, Int> = emptyMap(),
)

/** Pays a finished match (pot and completion coins). [None] pays nothing (tests, no economy). */
fun interface MatchEconomy {
    suspend fun settle(match: FinishedMatch): MatchCoins

    companion object {
        val None = MatchEconomy { MatchCoins() }
    }
}
