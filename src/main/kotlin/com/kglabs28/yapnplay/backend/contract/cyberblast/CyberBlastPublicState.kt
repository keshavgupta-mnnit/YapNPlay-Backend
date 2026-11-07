package com.kglabs28.yapnplay.backend.contract.cyberblast

import kotlinx.serialization.Serializable

/**
 * 0.13.0 Cyber Blast's `MatchSnapshot.publicState`: the board's size and
 * fixed rules, and the latest [frame] (with its blockers and walls). Frames
 * then come about [frameMs] apart as `MatchRelay`.
 */
@Serializable
data class CyberBlastPublicState(
    val cols: Int,
    val rows: Int,
    val lives: Int,
    val frameMs: Long,
    val fuseMs: Long,
    val boostMs: Long,
    /** 0.13.4: how the app shows and controls the game (admin settings). */
    val tuning: CyberClientTuning = CyberClientTuning(),
    /** 0.13.3: walking speed and with the boots (thousandths of a tile a second), so the app can move the user's own character at once. */
    val speed: Int = 3_200,
    val boostSpeed: Int = 4_800,
    /** 0.13.1: the room's Mini-map toggle (the app shows the whole arena small in a corner). */
    val miniMap: Boolean = false,
    val frame: CyberFrame,
)
