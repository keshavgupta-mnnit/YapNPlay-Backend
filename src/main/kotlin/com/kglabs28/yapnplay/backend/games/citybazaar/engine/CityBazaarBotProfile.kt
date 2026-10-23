package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BOT_BUY_RESERVE

/**
 * How boldly the bot plays a seat: the least cash it keeps after paying
 * ([reserve]) and the extra cushion on top of the expected bills
 * ([safetyBuffer]). [tuning] holds the shared rules (runtime settings
 * cb.bot_*). [plays] false only rolls, moves and stays in jail (a timed-out
 * turn).
 */
data class CityBazaarBotProfile(
    val reserve: Int,
    val safetyBuffer: Int,
    val plays: Boolean = true,
    val tuning: CityBazaarBotTuning = CityBazaarBotTuning(),
) {
    companion object {
        /** CPU seats, away and bot-held seats. */
        val CPU = CityBazaarBotProfile(reserve = BOT_BUY_RESERVE, safetyBuffer = 1_000)

        /** A player's Autoplay: a little bolder than a CPU. */
        val AUTOPLAY = CityBazaarBotProfile(reserve = 500, safetyBuffer = 500)

        /** The owner let the timer run out: roll and move only. */
        val CAUTIOUS = CityBazaarBotProfile(0, 0, plays = false)
    }
}

/**
 * The bot's judgement (runtime settings cb.bot_* and bot.standing_*).
 * Risk = chance of running out of cash in the next two turns × net worth,
 * counted × [ruinWeightPct] % × the standing factor ([standingMinPct] %
 * when far behind the richest → [standingMaxPct] % when leading). The cash
 * to keep is what holds that chance at [ruinTargetPct] %. Keeping a city
 * from a rival it would help counts [denialWeightPct] % of their gain. A
 * house that a rival who can't pay is likely to land on soon adds
 * [pressureValue]. Jail: with cash ≥ [comfortPct] % of the cash to keep and
 * half rent costing more than the fine, pay; only [jailPostponePct] % of the
 * rent dodged in jail counts as saved. With no round limit, [defaultRounds]
 * rounds are assumed left.
 */
data class CityBazaarBotTuning(
    val ruinWeightPct: Int = 200,
    val denialWeightPct: Int = 100,
    val ruinTargetPct: Int = 10,
    val pressureValue: Int = 2_000,
    val comfortPct: Int = 200,
    val jailPostponePct: Int = 30,
    val standingMinPct: Int = 60,
    val standingMaxPct: Int = 140,
    val defaultRounds: Int = 20,
)
