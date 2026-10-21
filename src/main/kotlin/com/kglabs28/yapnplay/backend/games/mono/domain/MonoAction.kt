package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import kotlinx.serialization.Serializable

@Serializable
sealed class MonoAction : Action {
    data class PlayCard(val handIndex: Int) : MonoAction()
    data object DrawCard : MonoAction()
    data class ChooseColor(val color: MonoConstants.Color) : MonoAction()
    data object CallMono : MonoAction()
    data object AcceptStack : MonoAction()
    /** Ends the turn after drawing a playable card and declining to play it. */
    data object Pass : MonoAction()
    /** The Rule of 7: the seat that played it picks whose hand to trade. */
    data class ChooseSwapTarget(val seat: Int) : MonoAction()
    /**
     * The MONO! window ran out. The host sends this on the seat's behalf;
     * it is the only action allowed to a seat that is not the actor.
     */
    data object ExpireMonoCall : MonoAction()

    /** The server ends the match (an end-game vote passed). */
    data object EndMatch : MonoAction()
}
