package com.kglabs28.yapnplay.backend.application.match.actor

import kotlinx.serialization.json.JsonObject

/** Everything a user can ask a match to do. Small hierarchy, kept together. */
sealed interface MatchAction {
    /** A game move: the raw message [type] and payload, decoded by the game. */
    data class Play(val type: String, val payload: JsonObject) : MatchAction

    /** Snapshot for a player, or start watching (room members only). */
    data class RequestSnapshot(val displayName: String, val avatarId: String = "") : MatchAction

    data class SetAutoplay(val enabled: Boolean) : MatchAction
    data class SetMic(val on: Boolean) : MatchAction
    data class SetTalkingMuted(val talking: Boolean) : MatchAction
    data class SetPresence(val away: Boolean) : MatchAction
    data object ConfirmPresence : MatchAction
    data class Leave(val giveToUserId: String?) : MatchAction
    data class AnswerOffer(val accept: Boolean) : MatchAction
    data class ChooseTaker(val giveToUserId: String?) : MatchAction
    data class OfferBotSeat(val seat: Int, val toUserId: String) : MatchAction
    data class TakeSeat(val seat: Int) : MatchAction
    /** The keep-playing vote at the game's limit. */
    data class VoteExtension(val keepPlaying: Boolean) : MatchAction
    /** Ask everyone to end the match early. */
    data object ProposeEnd : MatchAction
    /** A vote on someone's request to end the match. */
    data class VoteEnd(val agree: Boolean) : MatchAction

    /** The user's connection dropped (sent by the server, not the user). */
    data object Disconnected : MatchAction

    /** The user left the room: stop watching. */
    data object StopWatching : MatchAction
}
