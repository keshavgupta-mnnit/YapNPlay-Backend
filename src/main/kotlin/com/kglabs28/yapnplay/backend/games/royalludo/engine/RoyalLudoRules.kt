package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import com.kglabs28.yapnplay.backend.games.royalludo.utils.PIECES_PER_COLOUR
import kotlinx.serialization.Serializable

/**
 * Mandatory rules — the identity of Royal Ludo. Never toggled.
 */
@Serializable
data class RoyalLudoMandatoryRules(
    val piecesPerColour: Int = PIECES_PER_COLOUR,
    val exactFinish: Boolean = true,
    val capturesSendToBase: Boolean = true,
) : MandatoryRules

/**
 * House rules — private rooms may override these later.
 */
@Serializable
data class RoyalLudoHouseRules(
    val baseExitRolls: Set<Int> = setOf(6),
    val extraTurnOnSix: Boolean = true,
    val extraTurnOnCapture: Boolean = true,
    val extraTurnOnHome: Boolean = true,
    val threeSixesForfeit: Boolean = true,
    val pairProtection: Boolean = true,
    val autoMoveWhenSingleOption: Boolean = true,
) : HouseRules

/**
 * Full rules config for Royal Ludo: mandatory core + toggleable house rules.
 */
@Serializable
data class RoyalLudoRules(
    override val mandatory: RoyalLudoMandatoryRules = RoyalLudoMandatoryRules(),
    override val house: RoyalLudoHouseRules = RoyalLudoHouseRules(),
) : GameRules {
    override fun isAllowed(mode: PlayMode): Boolean = when (mode) {
        PlayMode.PRIVATE_ROOM -> true
        PlayMode.PUBLIC_MATCH -> this == TRADITIONAL
    }

    companion object {
        /** Quick Play's rules: traditional, with no pair protection (rooms may switch it on). */
        val TRADITIONAL = RoyalLudoRules(house = RoyalLudoHouseRules(pairProtection = false))
    }
}
