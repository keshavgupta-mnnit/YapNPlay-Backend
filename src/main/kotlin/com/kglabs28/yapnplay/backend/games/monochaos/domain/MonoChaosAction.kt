package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants
import kotlinx.serialization.Serializable

@Serializable
sealed class MonoChaosAction : Action {
    data class PlayCard(val handIndex: Int) : MonoChaosAction()
    data object DrawUntilPlayable : MonoChaosAction()
    data class ChooseColor(val color: MonoChaosConstants.Color) : MonoChaosAction()
    data class ChooseRouletteColor(val color: MonoChaosConstants.Color) : MonoChaosAction()
    data object CallMono : MonoChaosAction()
    data class StackDraw(val handIndex: Int) : MonoChaosAction()
    data object AcceptStack : MonoChaosAction()
    /** The Rule of 7: the seat that played it picks whose hand to trade. */
    data class ChooseSwapTarget(val seat: Int) : MonoChaosAction()
    /** Ends the turn after drawing a playable card and declining to play it. */
    data object Pass : MonoChaosAction()
    /**
     * The MONO! window ran out. The host sends this on the seat's behalf;
     * it is the only action allowed to a seat that is not the actor.
     */
    data object ExpireMonoCall : MonoChaosAction()

    /** The server ends the match (an end-game vote passed). */
    data object EndMatch : MonoChaosAction()

    /** Any other seat says the player on one card didn't call MONO!. */
    data object Caught : MonoChaosAction()
}
