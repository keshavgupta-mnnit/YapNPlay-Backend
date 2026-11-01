package com.kglabs28.yapnplay.backend.contract.doodleking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Doodle King actions inside `GameAction.action`; `"type"` names the action. */
@Serializable
sealed interface DoodleKingWireAction {
    /** The drawer picks one of the 3 words ([index] 0–2). */
    @Serializable @SerialName("PickWord") data class PickWord(val index: Int) : DoodleKingWireAction
    /** A guess (or, once guessed, a chat line for the others who guessed). */
    @Serializable @SerialName("Guess") data class Guess(val text: String) : DoodleKingWireAction
    /** The drawer gives up the turn (−50). */
    @Serializable @SerialName("PassTurn") data object Pass : DoodleKingWireAction
}

/** The drawing's messages, forwarded as they are (`MatchRelay`); `"type"` names each. */
@Serializable
sealed interface DoodleRelay

/**
 * Part of one stroke, sent by the drawer about every 60 ms and forwarded
 * as it is to everyone else (`MatchRelay`). The first part of stroke [id]
 * starts it with its [colour] and [size]; later parts add [points]. Points
 * are x,y pairs on a 4:3 canvas, each 0–4095.
 */
@Serializable
@SerialName("DoodleStroke")
data class DoodleStroke(
    val drawingId: Int,
    val id: Int,
    /** 0 black, 1 red, 2 blue, 3 green, 4 yellow. */
    val colour: Int = 0,
    /** 0 thin, 1 medium, 2 thick. */
    val size: Int = 1,
    /** Draws in the canvas colour. */
    val eraser: Boolean = false,
    val points: List<Int> = emptyList(),
) : DoodleRelay

/** The drawer took back their last stroke. */
@Serializable
@SerialName("DoodleUndo")
data class DoodleUndo(val drawingId: Int) : DoodleRelay

/** The drawer cleared the canvas. */
@Serializable
@SerialName("DoodleClear")
data class DoodleClear(val drawingId: Int) : DoodleRelay

/** The whole drawing so far, sent to whoever asked (`DoodleSync`, which carries only the match id). */
@Serializable
@SerialName("DoodleDrawing")
data class DoodleDrawing(
    val drawingId: Int,
    val strokes: List<DoodleStroke>,
) : DoodleRelay

/** Doodle King's match events (`MatchSnapshot.events`); `"type"` names each. */
@Serializable
sealed interface DoodleKingWireEvent {
    /** Someone guessed the word: +[points] (the table's cheer). */
    @Serializable @SerialName("DoodleGuessed") data class Guessed(val seat: Int, val points: Int) : DoodleKingWireEvent

    /** The turn is over: the word, and what each seat scored this turn. */
    @Serializable @SerialName("DoodleRevealed") data class Revealed(val word: String, val points: Map<Int, Int>) : DoodleKingWireEvent
}
