package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.ROUND_LIMIT
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.START_BONUS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.STARTING_CASH
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TIME_LIMIT_MINUTES

/** Mandatory rules — the identity of City Bazaar. [startingCash] comes from AppManager. */
data class CityBazaarMandatoryRules(
    val startingCash: Int = STARTING_CASH,
    val startBonus: Int = START_BONUS,
) : MandatoryRules

/**
 * House rules — a room leader may change these. [roundLimit] /
 * [timeLimitMinutes] null = off; their values come from AppManager.
 */
data class CityBazaarHouseRules(
    val setDoubleRent: Boolean = true,
    val restHouseSkips: Boolean = true,
    val clubFee: Boolean = true,
    val roundLimit: Int? = ROUND_LIMIT,
    val timeLimitMinutes: Int? = TIME_LIMIT_MINUTES,
) : HouseRules

data class CityBazaarRules(
    override val mandatory: CityBazaarMandatoryRules = CityBazaarMandatoryRules(),
    override val house: CityBazaarHouseRules = CityBazaarHouseRules(),
) : GameRules {
    // Remote values (cash, limits) may change; any toggle set is allowed everywhere for now.
    override fun isAllowed(mode: PlayMode): Boolean = true

    companion object {
        val STANDARD = CityBazaarRules()
    }
}
