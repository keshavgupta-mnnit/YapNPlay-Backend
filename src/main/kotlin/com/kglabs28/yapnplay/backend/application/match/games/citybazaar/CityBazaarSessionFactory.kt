package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BANK_START
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.EXTENSION_ROUNDS
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarHouseRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarRules
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarState
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.ROUND_LIMIT
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
    /** Rounds before the richest player wins (server setting CITY_BAZAAR_ROUND_LIMIT). */
    private val roundLimit: Int = ROUND_LIMIT,
    /** Rounds added when the players vote to keep going (CITY_BAZAAR_EXTENSION_ROUNDS). */
    private val extensionRounds: Int = EXTENSION_ROUNDS,
    /** The bank's starting reserve (CITY_BAZAAR_BANK_START). */
    private val bankStart: Int = BANK_START,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        // Quick Play: every rule on, with the server's round limit.
        val rules = houseRules?.let(::rulesFrom)
            ?: CityBazaarRules.STANDARD.copy(house = CityBazaarHouseRules(roundLimit = roundLimit, extensionRounds = extensionRounds, bankStart = bankStart))
        val state = game.start(rules, seatCount, random) as CityBazaarState
        return CityBazaarSession(game, state, random, json, clock)
    }

    /** A running auction gets its full bidding window again: nobody could bid while the server was down. */
    override fun restore(saved: JsonObject): GameSession {
        val session = json.decodeFromJsonElement(CityBazaarSaved.serializer(), saved)
        val now = clock.nowMillis()
        val bidding = session.engine.auction != null
        val auctionEndsAt = if (bidding) now + AUCTION_SECONDS * BackendConstants.MILLIS_PER_SECOND else session.auctionEndsAt
        val lastBidAt = if (bidding) now else session.lastBidAt
        return CityBazaarSession(game, session.engine, random, json, clock, session.startedAt, auctionEndsAt, lastBidAt)
    }

    private fun rulesFrom(toggles: Map<String, Boolean>): CityBazaarRules {
        val defaults = CityBazaarHouseRules()
        fun on(key: String) = toggles[key] ?: true
        return CityBazaarRules.STANDARD.copy(
            house = defaults.copy(
                setDoubleRent = on(SET_DOUBLE_RENT),
                restHouseSkips = on(REST_HOUSE_SKIP),
                clubFee = on(CLUB_FEE),
                roundLimit = if (on(ROUND_LIMIT_KEY)) roundLimit else null,
                timeLimitMinutes = if (on(TIME_LIMIT_KEY)) TIME_LIMIT_MINUTES else null,
                extensionRounds = extensionRounds,
                bankStart = bankStart,
            ),
        )
    }

    private companion object {
        // Keys of CITY_BAZAAR_HOUSE_RULES.
        const val SET_DOUBLE_RENT = "set_double_rent"
        const val REST_HOUSE_SKIP = "rest_house_skip"
        const val CLUB_FEE = "club_fee"
        const val ROUND_LIMIT_KEY = "round_limit"
        const val TIME_LIMIT_KEY = "time_limit"
    }
}
