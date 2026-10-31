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
    /** Caught without calling MONO!: the player on one card draws this many. */
    val monoPenaltyCards: Int = 5,
    /** A wrong catch (they had called): the catcher draws this many. */
    val falseCatchPenaltyCards: Int = 2,
) : MandatoryRules

@Serializable
data class MonoHouseRules(
    /** A power card (anything but a number) may never be the last card played. On unless a group turns it off. */
    val noPowerFinish: Boolean = true,
    /** A Wild +4 may go down even while holding the colour in play (classic: only when you have none). */
    val drawFourAnytime: Boolean = false,
    /** MONO! must be called on your own turn with two cards, before playing the second-last. */
    val callBeforePlay: Boolean = false,
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
