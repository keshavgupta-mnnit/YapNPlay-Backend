package com.kglabs28.yapnplay.backend.application.economy

import com.kglabs28.yapnplay.backend.contract.AdRewardStatus
import com.kglabs28.yapnplay.backend.contract.EmojiGiftPrice
import com.kglabs28.yapnplay.backend.contract.EmojiGiftPrices
import com.kglabs28.yapnplay.backend.domain.economy.EmojiGifts
import com.kglabs28.yapnplay.backend.domain.economy.AdRefusal
import com.kglabs28.yapnplay.backend.domain.economy.AdClaimResult
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
import com.kglabs28.yapnplay.backend.domain.economy.adRewardFor
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
     * A fully watched rewarded ad: the day's next step of the ladder
     * (200, 300, 400, 500, 700 by default), a few times a day. [adId] is unique
     * per ad view, so a retry or replay pays nothing. [ads] is the hook for
     * AdMob server-side verification (SSV) later; today it accepts every view.
     */
    suspend fun adReward(userId: String, adId: String): CoinsReward {
        if (adId.isBlank() || adId.length > CoinLedgerTable.REF_MAX_LENGTH) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_AD_ID)
        if (!ads.verify(userId, adId)) throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.BAD_AD_ID)
        val now = clock.nowMillis()
        val limit = settings[GameSettings.AD_DAILY_LIMIT]
        val claim = when (val result = store.claimAdReward(userId, adId, dayOf(now), rewards(limit), limit, cooldownMillis(), now)) {
            is AdClaimResult.Paid -> result.claim
            is AdClaimResult.Refused -> throw ServiceException(
                ErrorCode.RATE_LIMITED,
                if (result.reason == AdRefusal.DAILY_LIMIT) BackendStrings.AD_REWARDS_USED else BackendStrings.AD_COOLDOWN,
            )
        }
        return CoinsReward(
            coins = store.balance(userId),
            granted = claim.granted,
            adsLeftToday = claim.adsLeftToday,
            dailyLimit = limit,
            nextAdAt = nextAdAt(claim.adsLeftToday, claim.lastAdAt, now),
            adRewards = rewards(limit),
        )
    }

    /** `GET /coins/ad-status`: what the next ad pays, the day's ladder, how many are left today, and when the next one can pay. */
    suspend fun adStatus(userId: String): AdRewardStatus {
        val now = clock.nowMillis()
        val limit = settings[GameSettings.AD_DAILY_LIMIT]
        val usage = store.adUsage(userId, dayOf(now))
        val left = (limit - usage.usedToday).coerceAtLeast(0)
        val ladder = rewards(limit)
        val next = if (left > 0) adRewardFor(ladder, usage.usedToday) else 0
        return AdRewardStatus(next, limit, left, nextAdAt(left, usage.lastAdAt, now), now, ladder)
    }

    /** The day's ladder, one amount per allowed ad (ads past the setting's end pay its last amount). */
    private fun rewards(limit: Int): List<Int> {
        val ladder = settings[GameSettings.AD_REWARDS]
        return List(limit) { adRewardFor(ladder, it) }
    }

    /** Null when an ad can pay now; the next India-time day when today's are used up; else the end of the gap since the last one. */
    private fun nextAdAt(adsLeftToday: Int, lastAdAt: Long, now: Long): Long? {
        if (adsLeftToday <= 0) return Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate().plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli()
        val ready = lastAdAt + cooldownMillis()
        return if (lastAdAt > 0 && ready > now) ready else null
    }

    private fun cooldownMillis(): Long = settings[GameSettings.AD_COOLDOWN_MINUTES] * MILLIS_PER_MINUTE

    suspend fun balance(userId: String): Int = store.balance(userId)

    /** `GET /coins/emoji-gifts`: the emojis on sale with their prices, and the user's coins. */
    suspend fun emojiGiftPrices(userId: String): EmojiGiftPrices {
        val prices = settings[GameSettings.EMOJI_GIFT_PRICES]
        return EmojiGiftPrices(store.balance(userId), EmojiGifts.IDS.mapNotNull { id -> EmojiGifts.priceOf(id, prices)?.let { EmojiGiftPrice(id, it) } })
    }

    /** What [emojiId] costs now; BAD_REQUEST when it is not on sale. */
    fun emojiGiftPrice(emojiId: String): Int =
        EmojiGifts.priceOf(emojiId, settings[GameSettings.EMOJI_GIFT_PRICES]) ?: throw ServiceException(ErrorCode.BAD_REQUEST, BackendStrings.UNKNOWN_EMOJI)

    /** Takes [coins] for an emoji sent at [matchId]: the new balance, or NOT_ENOUGH_COINS (nothing taken). */
    suspend fun payForEmojiGift(userId: String, matchId: String, coins: Int): Int =
        store.change(userId, -coins, CoinReason.EMOJI_GIFT, matchId, clock.nowMillis())
            ?: throw ServiceException(ErrorCode.NOT_ENOUGH_COINS, BackendStrings.NOT_ENOUGH_COINS_EMOJI)

    /** Gives back an emoji's [coins] the table refused (the seat left, the match ended). */
    suspend fun refundEmojiGift(userId: String, matchId: String, coins: Int) {
        store.change(userId, coins, CoinReason.EMOJI_GIFT, matchId, clock.nowMillis())
    }

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
