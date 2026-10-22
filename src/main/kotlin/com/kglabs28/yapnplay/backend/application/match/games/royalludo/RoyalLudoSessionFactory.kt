package com.kglabs28.yapnplay.backend.application.match.games.royalludo

import com.kglabs28.yapnplay.backend.application.match.games.BotThinkTime
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoBotTuning
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoBot
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.application.match.games.GameSessionFactory
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoEngine
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoHouseRules
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoRules
import com.kglabs28.yapnplay.backend.games.royalludo.engine.RoyalLudoState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Starts Royal Ludo sessions; a room's house-rule toggles map onto the engine's house rules. */
class RoyalLudoSessionFactory(
    override val game: RoyalLudoEngine,
    private val random: GameRandom,
    private val json: Json,
    /** Turn and CPU think time (runtime settings). */
    private val settings: Settings = Settings.Defaults,
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val rules = houseRules?.let(::rulesFrom) ?: RoyalLudoRules.TRADITIONAL
        val state = (game.start(rules, seatCount, random) as RoyalLudoState).copy(
            sixBoostPerMissPct = settings[GameSettings.RL_SIX_BOOST_PER_MISS_PCT],
            sixChanceCapPct = settings[GameSettings.RL_SIX_CHANCE_CAP_PCT],
        )
        return RoyalLudoSession(game, state, random, json, settings[GameSettings.RL_TURN_SECONDS], think(), BotTrace().let { RoyalLudoSessionBots(bot(it), it) })
    }

    private fun think() = BotThinkTime(
        settings[GameSettings.RL_CPU_THINK_MS].toLong(),
        settings[GameSettings.RL_CPU_THINK_JITTER_MS].toLong(),
        settings[GameSettings.RL_CPU_DECISION_EXTRA_MS].toLong(),
    )

    override fun restore(saved: JsonObject): GameSession =
        RoyalLudoSession(
            game, json.decodeFromJsonElement(RoyalLudoState.serializer(), saved), random, json,
            settings[GameSettings.RL_TURN_SECONDS], think(), BotTrace().let { RoyalLudoSessionBots(bot(it), it) },
        )

    private fun bot(trace: BotTrace) = RoyalLudoBot(
        RoyalLudoBotTuning(
            baseCost = settings[GameSettings.RL_BOT_BASE_COST],
            captureWeightPct = settings[GameSettings.RL_BOT_CAPTURE_WEIGHT_PCT],
            endgameCapturePct = settings[GameSettings.RL_BOT_ENDGAME_CAPTURE_PCT],
            extraTurnBonus = settings[GameSettings.RL_BOT_EXTRA_TURN],
            finishBonus = settings[GameSettings.RL_BOT_FINISH],
            leaveBaseBonus = settings[GameSettings.RL_BOT_LEAVE_BASE],
            flexBonus = settings[GameSettings.RL_BOT_FLEX],
            riskWeightPct = settings[GameSettings.RL_BOT_RISK_WEIGHT_PCT],
            chaseBonus = settings[GameSettings.RL_BOT_CHASE],
            standingMinPct = settings[GameSettings.BOT_STANDING_MIN_PCT],
            standingMaxPct = settings[GameSettings.BOT_STANDING_MAX_PCT],
        ),
        trace,
    )

    private fun rulesFrom(toggles: Map<String, Boolean>): RoyalLudoRules {
        val defaults = RoyalLudoHouseRules()
        fun on(key: String, default: Boolean) = toggles[key] ?: default
        return RoyalLudoRules(
            house = defaults.copy(
                extraTurnOnSix = on(EXTRA_TURN_ON_SIX, defaults.extraTurnOnSix),
                extraTurnOnCapture = on(EXTRA_TURN_ON_CAPTURE, defaults.extraTurnOnCapture),
                extraTurnOnHome = on(EXTRA_TURN_ON_HOME, defaults.extraTurnOnHome),
                threeSixesForfeit = on(THREE_SIXES_FORFEIT, defaults.threeSixesForfeit),
                pairProtection = on(PAIR_PROTECTION, defaults.pairProtection),
                autoMoveWhenSingleOption = on(AUTO_MOVE, defaults.autoMoveWhenSingleOption),
            ),
        )
    }

    private companion object {
        // Keys of ROYAL_LUDO_HOUSE_RULES.
        const val EXTRA_TURN_ON_SIX = "extra_turn_on_six"
        const val EXTRA_TURN_ON_CAPTURE = "extra_turn_on_capture"
        const val EXTRA_TURN_ON_HOME = "extra_turn_on_home"
        const val THREE_SIXES_FORFEIT = "three_sixes_forfeit"
        const val PAIR_PROTECTION = "pair_protection"
        const val AUTO_MOVE = "auto_move_single_option"
    }
}
