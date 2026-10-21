package com.kglabs28.yapnplay.backend.games.monochaos.domain.rules

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import kotlinx.serialization.Serializable

@Serializable
data class MonoChaosMandatoryRules(
    val startingHandSize: Int = MonoChaosConstants.STARTING_HAND_SIZE,
    val deckSize: Int = MonoChaosConstants.DECK_SIZE,
    val knockoutThreshold: Int = MonoChaosConstants.KNOCKOUT_THRESHOLD,
    val drawUntilPlayable: Boolean = true,
    val monoPenaltyCards: Int = 4,
) : MandatoryRules

@Serializable
data class MonoChaosHouseRules(
    val stackingAllowed: Boolean = true,
    val sevenSwap: Boolean = true,
    val zeroPass: Boolean = true,
    val knockoutEnabled: Boolean = true,
) : HouseRules

@Serializable
data class MonoChaosRules(
    override val mandatory: MonoChaosMandatoryRules = MonoChaosMandatoryRules(),
    override val house: MonoChaosHouseRules = MonoChaosHouseRules(),
) : GameRules {
    override fun isAllowed(mode: PlayMode): Boolean = when (mode) {
        PlayMode.PRIVATE_ROOM -> true
        PlayMode.PUBLIC_MATCH -> this == TRADITIONAL
    }

    companion object {
        val TRADITIONAL = MonoChaosRules()
    }
}
