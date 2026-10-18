package com.kglabs28.yapnplay.backend.application.match.games.royalludo

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
) : GameSessionFactory {

    override fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession {
        val rules = houseRules?.let(::rulesFrom) ?: RoyalLudoRules.TRADITIONAL
        val state = game.start(rules, seatCount, random) as RoyalLudoState
        return RoyalLudoSession(game, state, random, json)
    }

    override fun restore(saved: JsonObject): GameSession =
        RoyalLudoSession(game, json.decodeFromJsonElement(RoyalLudoState.serializer(), saved), random, json)

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
