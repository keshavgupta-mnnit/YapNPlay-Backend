package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.EXTENSION_MINUTES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BANK_START
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CLUB_REST_EACH
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.EXTENSION_ROUNDS
import kotlinx.serialization.Serializable

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.ROUND_LIMIT
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.START_BONUS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.STARTING_CASH
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TIME_LIMIT_MINUTES

/** Mandatory rules — the identity of City Bazaar. [startingCash] comes from AppManager. */
@Serializable
data class CityBazaarMandatoryRules(
    val startingCash: Int = STARTING_CASH,
    val startBonus: Int = START_BONUS,
) : MandatoryRules

/**
 * House rules — a room leader may change these. [roundLimit] /
 * [timeLimitMinutes] null = off; their values come from AppManager.
 */
@Serializable
data class CityBazaarHouseRules(
    val setDoubleRent: Boolean = true,
    /** Rest House on: every other player pays the lander [clubRestEach] (stored under its old name). */
    val restHouseSkips: Boolean = true,
    /** Club on: the lander pays every other player [clubRestEach]. */
    val clubFee: Boolean = true,
    val clubRestEach: Int = CLUB_REST_EACH,
    /** In debt, selling a city puts it up for auction (no bids: the bank buys it at half). Off: straight to the bank. */
    val debtAuction: Boolean = true,
    /** Show a popup for every payment (off: a quick coin animation). Display only; the app reads it from the public state. */
    val moneyPopups: Boolean = true,
    val roundLimit: Int? = ROUND_LIMIT,
    val timeLimitMinutes: Int? = TIME_LIMIT_MINUTES,
    /** Rounds added when the players vote to keep going at the round limit. */
    val extensionRounds: Int = EXTENSION_ROUNDS,
    /** Minutes added when they vote to keep going after the time limit. */
    val extensionMinutes: Int = EXTENSION_MINUTES,
    /** The bank's starting reserve (shown, never limits play). */
    val bankStart: Int = BANK_START,
) : HouseRules

@Serializable
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
