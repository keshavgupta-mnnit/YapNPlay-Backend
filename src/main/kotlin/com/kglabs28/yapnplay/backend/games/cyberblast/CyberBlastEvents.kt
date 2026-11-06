package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent

/** The player pushes [dir] (null: lets go, stands still). */
data class BlastSteer(val dir: BlastDir?) : Action

/** The player drops a bomb where they stand. */
data object BlastDropBomb : Action

/** The match clock moves on by [elapsedMs] (the session's frame). */
data class BlastAdvance(val elapsedMs: Long) : Action

/** The match ends now (an early-end vote, everyone gone), ranked as it stands. */
data object BlastEnd : Action

sealed interface BlastEvent : GameEvent

/**
 * [victim] lost a heart to [by]'s blast ([self]: their own bomb; [crushed]: the closing
 * arena, nobody's): [victimPoints] and [byPoints] are what each scored for it.
 */
data class BlastHit(
    val victim: Int,
    val by: Int,
    val livesLeft: Int,
    val victimPoints: Int,
    val byPoints: Int,
    val self: Boolean,
    val crushed: Boolean = false,
) : BlastEvent

/** [seat] has no hearts left. */
data class BlastOut(val seat: Int) : BlastEvent

/** Blockers [tiles] broke this step (the shatter on the board), and the bombs that went off. */
data class BlastBoom(val bombs: List<Int>, val crates: List<Int>) : BlastEvent

/** [seat] picked up speed boots. */
data class BlastBoosted(val seat: Int) : BlastEvent

/** The countdown is over: everyone may move. */
data object BlastStarted : BlastEvent

data object BlastFinished : BlastEvent
