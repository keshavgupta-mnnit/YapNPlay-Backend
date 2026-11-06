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
    /** 0.13.1: the room's Mini-map toggle (the app shows the whole arena small in a corner). */
    val miniMap: Boolean = false,
    val frame: CyberFrame,
)
