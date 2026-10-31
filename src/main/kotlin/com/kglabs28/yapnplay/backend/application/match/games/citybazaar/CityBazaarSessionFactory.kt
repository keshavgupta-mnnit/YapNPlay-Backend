package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

import com.kglabs28.yapnplay.backend.application.match.games.BotThinkTime
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBotTuning
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBotProfile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBot
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarHouseRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarState
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TIME_LIMIT_MINUTES
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.AUCTION_SECONDS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Starts City Bazaar sessions; a room's toggles switch the house rules and the round/time limits. */
class CityBazaarSessionFactory(
    override val game: CityBazaarEngine,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    /** Round limit, extensions, bank, Club/Rest amount and timing (runtime settings). */
    private val settings: Settings = Settings.Defaults,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        // Quick Play: every rule on, with the server's round limit.
        val rules = CityBazaarRules.STANDARD.copy(
            // A bigger table starts poorer, so the board still runs short of cities.
            mandatory = CityBazaarRules.STANDARD.mandatory.copy(
                startingCash = settings[if (seatCount > SMALL_TABLE_SEATS) GameSettings.CB_START_CASH_LARGE else GameSettings.CB_START_CASH],
            ),
            house = houseRulesFrom(houseRules.orEmpty()),
        )
        val state = game.start(rules, seatCount, random) as CityBazaarState
        val trace = BotTrace()
        return CityBazaarSession(game, state, random, json, clock, timingOf = ::timing, trace = trace, autoplayBot = autoplayBot(trace), cpuBot = cpuBot(trace))
    }

    private fun autoplayBot(trace: BotTrace) = CityBazaarBot(
        CityBazaarBotProfile(settings[GameSettings.CB_AUTO_BUY_RESERVE], settings[GameSettings.CB_AUTO_SAFETY_BUFFER], tuning = tuning()),
        trace,
    )

    private fun cpuBot(trace: BotTrace) = CityBazaarBot(
        CityBazaarBotProfile(settings[GameSettings.CB_CPU_RESERVE], settings[GameSettings.CB_CPU_SAFETY_BUFFER], tuning = tuning()),
        trace,
    )

    private fun tuning() = CityBazaarBotTuning(
        ruinWeightPct = settings[GameSettings.CB_BOT_RUIN_WEIGHT_PCT],
        denialWeightPct = settings[GameSettings.CB_BOT_DENIAL_WEIGHT_PCT],
        ruinTargetPct = settings[GameSettings.CB_BOT_RUIN_TARGET_PCT],
        pressureValue = settings[GameSettings.CB_BOT_PRESSURE_VALUE],
        comfortPct = settings[GameSettings.CB_BOT_COMFORT_PCT],
        jailPostponePct = settings[GameSettings.CB_BOT_JAIL_POSTPONE_PCT],
        standingMinPct = settings[GameSettings.BOT_STANDING_MIN_PCT],
        standingMaxPct = settings[GameSettings.BOT_STANDING_MAX_PCT],
        defaultRounds = settings[GameSettings.CB_BOT_DEFAULT_ROUNDS],
    )

    private fun timing() = CityBazaarTiming(
        turnSeconds = settings[GameSettings.CB_TURN_SECONDS],
        debtSeconds = settings[GameSettings.CB_DEBT_SECONDS],
        voteGapMillis = settings[GameSettings.CB_VOTE_GAP_MINUTES] * SECONDS_PER_MINUTE * BackendConstants.MILLIS_PER_SECOND,
        bidDelayMillis = settings[GameSettings.CB_CPU_BID_DELAY_MS].toLong(),
        firstBidDelayMillis = settings[GameSettings.CB_CPU_FIRST_BID_DELAY_MS].toLong(),
        think = BotThinkTime(
            settings[GameSettings.CB_CPU_THINK_MS].toLong(),
            settings[GameSettings.CB_CPU_THINK_JITTER_MS].toLong(),
            settings[GameSettings.CB_CPU_DECISION_EXTRA_MS].toLong(),
        ),
        messageMillis = settings[GameSettings.CB_MESSAGE_PAUSE_MS].toLong(),
        maxMessageMillis = settings[GameSettings.CB_MESSAGE_PAUSE_CAP_MS].toLong(),
    )

    /** A running auction gets its full bidding window again: nobody could bid while the server was down. */
    override fun restore(saved: JsonObject): GameSession {
        val session = json.decodeFromJsonElement(CityBazaarSaved.serializer(), saved)
        val now = clock.nowMillis()
        val bidding = session.engine.auction != null
        val auctionEndsAt = if (bidding) now + AUCTION_SECONDS * BackendConstants.MILLIS_PER_SECOND else session.auctionEndsAt
        val lastBidAt = if (bidding) now else session.lastBidAt
        val trace = BotTrace()
        return CityBazaarSession(game, session.engine, random, json, clock, session.startedAt, auctionEndsAt, lastBidAt, ::timing, trace = trace, autoplayBot = autoplayBot(trace), cpuBot = cpuBot(trace))
    }

    /** A room's toggles (none for Quick Play: every rule on) plus the server's settings. */
    private fun houseRulesFrom(toggles: Map<String, Boolean>): CityBazaarHouseRules {
        fun on(key: String) = toggles[key] ?: true
        return CityBazaarHouseRules(
            setDoubleRent = on(SET_DOUBLE_RENT),
            restHouseSkips = on(REST_HOUSE_SKIP),
            clubFee = on(CLUB_FEE),
            debtAuction = on(DEBT_AUCTION),
            moneyPopups = on(MONEY_POPUPS),
            roundLimit = if (on(ROUND_LIMIT_KEY)) settings[GameSettings.CB_ROUND_LIMIT] else null,
            timeLimitMinutes = if (on(TIME_LIMIT_KEY)) settings[GameSettings.CB_TIME_LIMIT_MINUTES] else null,
            extensionRounds = settings[GameSettings.CB_EXTENSION_ROUNDS],
            extensionMinutes = settings[GameSettings.CB_EXTENSION_MINUTES],
            bankStart = settings[GameSettings.CB_BANK_START],
            clubRestEach = settings[GameSettings.CB_CLUB_REST_EACH],
        )
    }

    private companion object {
        // Keys of CITY_BAZAAR_HOUSE_RULES.
        const val SET_DOUBLE_RENT = "set_double_rent"
        const val REST_HOUSE_SKIP = "rest_house_skip"
        const val CLUB_FEE = "club_fee"
        const val DEBT_AUCTION = "debt_auction"
        const val MONEY_POPUPS = "money_popups"
        const val ROUND_LIMIT_KEY = "round_limit"
        const val TIME_LIMIT_KEY = "time_limit"
    }
}

private const val SECONDS_PER_MINUTE = 60L
/** Up to this many players start with [GameSettings.CB_START_CASH]; more with [GameSettings.CB_START_CASH_LARGE]. */
private const val SMALL_TABLE_SEATS = 4
