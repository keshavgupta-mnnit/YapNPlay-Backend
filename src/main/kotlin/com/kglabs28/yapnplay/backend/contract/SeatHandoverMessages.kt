package com.kglabs28.yapnplay.backend.contract

import kotlinx.serialization.Serializable

// Seat handover and presence, shared by every game (handover: room matches
// only). Client → server: SetPresence, ConfirmPresence, LeaveSeat,
// AnswerSeatOffer, ChooseSeatTaker, TakeSeat. The current offer rides on the match snapshot.

/** The app went to the background ([away] true) or came back. */
@Serializable
data class SetPresence(val matchId: String, val away: Boolean)

/** "I'm still here": cancels the absence check after idle turns. */
@Serializable
data class ConfirmPresence(val matchId: String)

/** Leave the match; [giveToUserId] is the spectator to offer the seat to, null for the bot. */
@Serializable
data class LeaveSeat(val matchId: String, val giveToUserId: String?)

/** The named spectator's answer to a seat offer. */
@Serializable
data class AnswerSeatOffer(val matchId: String, val accept: Boolean)

/** The leader's pick after the first spectator passed; null for the bot. */
@Serializable
data class ChooseSeatTaker(val matchId: String, val giveToUserId: String?)

/** The room's leader or admin offers a seat the bot is playing to a spectator (who still has to accept). */
@Serializable
data class OfferBotSeat(val matchId: String, val seat: Int, val toUserId: String)

/** A spectator takes a seat open to all (its owner went away). */
@Serializable
data class TakeSeat(val matchId: String, val seat: Int)
