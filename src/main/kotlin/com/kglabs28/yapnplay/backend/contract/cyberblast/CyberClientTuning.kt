package com.kglabs28.yapnplay.backend.contract.cyberblast

import kotlinx.serialization.Serializable

/**
 * 0.13.4 How the app shows and controls Cyber Blast (admin settings), sent in
 * every snapshot so the feel and layout change without an app update. Sizes
 * are dp at a usual phone's scale; times are ms; percentages are of the pad.
 */
@Serializable
data class CyberClientTuning(
    /** Other players are drawn this far behind the newest frame, so they glide even when frames come unevenly. */
    val renderDelayMs: Int = 100,
    /** The pad ignores a thumb this near its middle (% of its size). */
    val padDeadZonePct: Int = 10,
    /** The direction being pushed stays until another is this much stronger (%). */
    val padKeepPct: Int = 115,
    /** A push this far off straight (% of its strength) also counts the side way, taken when the main way is blocked. */
    val padSidePct: Int = 25,
    /** The user's own character jumps to the server's place past this (thousandths of a tile)… */
    val snapMilli: Int = 1_400,
    /** …eases back past this while walking… */
    val driftMilli: Int = 700,
    /** …by this % each screen frame. */
    val easePct: Int = 15,
    val stepSoundMs: Int = 270,
    val boostedStepSoundMs: Int = 190,
    val sideWidthDp: Int = 168,
    val padSizeDp: Int = 130,
    val bombButtonDp: Int = 60,
    val miniMapHeightDp: Int = 72,
    /** A Big Arena's window: this many rows top to bottom, at least this many columns. */
    val viewCols: Int = 15,
    val viewRows: Int = 11,
)
