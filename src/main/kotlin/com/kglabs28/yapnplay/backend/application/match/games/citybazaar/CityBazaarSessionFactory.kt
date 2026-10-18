package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

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
import kotlinx.serialization.json.Json

/** Starts City Bazaar sessions; a room's toggles switch the house rules and the round/time limits. */
class CityBazaarSessionFactory(
    override val game: CityBazaarEngine,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val rules = houseRules?.let(::rulesFrom) ?: CityBazaarRules.STANDARD
        val state = game.start(rules, seatCount, random) as CityBazaarState
        return CityBazaarSession(game, state, random, json, clock)
    }

    private fun rulesFrom(toggles: Map<String, Boolean>): CityBazaarRules {
        val defaults = CityBazaarHouseRules()
        fun on(key: String) = toggles[key] ?: true
        return CityBazaarRules.STANDARD.copy(
            house = defaults.copy(
                setDoubleRent = on(SET_DOUBLE_RENT),
                restHouseSkips = on(REST_HOUSE_SKIP),
                clubFee = on(CLUB_FEE),
                roundLimit = if (on(ROUND_LIMIT_KEY)) ROUND_LIMIT else null,
                timeLimitMinutes = if (on(TIME_LIMIT_KEY)) TIME_LIMIT_MINUTES else null,
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
