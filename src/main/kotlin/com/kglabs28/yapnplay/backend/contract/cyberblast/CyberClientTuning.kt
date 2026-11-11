package com.kglabs28.yapnplay.backend.contract.cyberblast

import kotlinx.serialization.Serializable

/**
 * How much of a Big Arena every player sees (admin settings, the same for everyone, so nobody sees
 * further than the rest). Everything else about how the game looks and feels on screen (sizes, the
 * pad, smoothing, sounds) is each app's own and never comes from the server.
 */
@Serializable
data class CyberClientTuning(
    /** A Big Arena's window: this many rows top to bottom, at least this many columns. */
    val viewCols: Int = 15,
    val viewRows: Int = 11,
)
