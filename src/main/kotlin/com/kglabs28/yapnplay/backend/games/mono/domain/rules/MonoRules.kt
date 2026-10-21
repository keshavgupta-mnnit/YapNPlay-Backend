package com.kglabs28.yapnplay.backend.games.mono.domain.rules

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import kotlinx.serialization.Serializable

@Serializable
data class MonoMandatoryRules(
    val startingHandSize: Int = MonoConstants.STARTING_HAND_SIZE,
    val deckSize: Int = MonoConstants.DECK_SIZE,
    val knockoutThreshold: Int = 0,
    val drawUntilPlayable: Boolean = false,
    val monoPenaltyCards: Int = 2,
) : MandatoryRules

@Serializable
data class MonoHouseRules(
    val stackingAllowed: Boolean = false,
    val sevenSwap: Boolean = false,
    val zeroPass: Boolean = false,
) : HouseRules

@Serializable
data class MonoRules(
    override val mandatory: MonoMandatoryRules = MonoMandatoryRules(),
    override val house: MonoHouseRules = MonoHouseRules(),
) : GameRules {
    override fun isAllowed(mode: PlayMode): Boolean = when (mode) {
        PlayMode.PRIVATE_ROOM -> true
        PlayMode.PUBLIC_MATCH -> this == TRADITIONAL
    }

    companion object {
        val TRADITIONAL = MonoRules()
    }
}
