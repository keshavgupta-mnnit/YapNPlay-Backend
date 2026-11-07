package com.kglabs28.yapnplay.backend.contract.cyberblast

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 0.13.0 MATCH channel request `CyberInput`: the player pushes [dir]
 * (UP, DOWN, LEFT, RIGHT; null: lets go) and, with [bomb], drops a bomb
 * where they stand. Sent whenever either changes; acked, nothing forwarded
 * (the next [CyberFrame] shows it).
 */
@Serializable
data class CyberInput(
    val matchId: String,
    val dir: String? = null,
    /** 0.13.4: the side way also pushed (a diagonal on the pad), taken when [dir] is blocked. */
    val alt: String? = null,
    val bomb: Boolean = false,
    /** 0.13.3: with [bomb], the tile the player's screen shows them on (it runs ahead of the server); the bomb goes there when it is beside the server's tile. */
    val tile: Int? = null,
)

/** Cyber Blast's `MatchRelay` messages; `"type"` names each. */
@Serializable
sealed interface CyberRelay

/**
 * 0.13.0 `MatchRelay` payload, about 20 times a second while a Cyber Blast
 * match plays: the whole moving board. Positions are thousandths of a tile
 * (a tile's centre at +500); tiles are `row * cols + col`; times are ms
 * left. [crates] and [walls] are null when they didn't change since the
 * last frame (a snapshot's frame always has them).
 */
@Serializable
@SerialName("CyberFrame")
data class CyberFrame(
    /** Counts up by one each frame: an older one arriving late is dropped. */
    val frame: Long,
    /** The match's own clock (ms since the countdown began). */
    val time: Long,
    /** COUNTDOWN, PLAYING or FINISHED. */
    val phase: String,
    /** Until everyone may move (the countdown), else 0. */
    val countdownMs: Long = 0,
    /** Until the arena starts closing in; null: it never does, or it already is. */
    val closingInMs: Long? = null,
    val players: List<CyberPlayerWire>,
    val bombs: List<CyberBombWire> = emptyList(),
    val flames: List<CyberFlameWire> = emptyList(),
    val items: List<CyberItemWire> = emptyList(),
    val crates: List<Int>? = null,
    val walls: List<Int>? = null,
    /** Hearts lost since the last frame. */
    val hits: List<CyberHitWire> = emptyList(),
    /** Blockers broken and bombs gone off since the last frame (their tiles). */
    val broken: List<Int> = emptyList(),
    val booms: List<Int> = emptyList(),
) : CyberRelay

@Serializable
data class CyberPlayerWire(
    val seat: Int,
    val x: Int,
    val y: Int,
    /** UP, DOWN, LEFT or RIGHT. */
    val facing: String,
    val moving: Boolean,
    val lives: Int,
    val score: Int,
    /** How far their blast reaches (tiles). */
    val radius: Int,
    /** Speed boots time left (ms), 0: none. */
    val boostMs: Long = 0,
    /** Can't be hurt for this long (just came back), 0: none. */
    val shieldMs: Long = 0,
    /** No hearts left, or left the match. */
    val out: Boolean = false,
    /** 0.13.2: players they have hit in all, and in a row since they last lost a heart. */
    val kills: Int = 0,
    val streak: Int = 0,
)

@Serializable
data class CyberBombWire(val id: Int, val tile: Int, val owner: Int, val radius: Int, val fuseMs: Long)

/** Fire on [tile] from [owner]'s bomb; [shape] CENTRE, HORIZONTAL or VERTICAL. */
@Serializable
data class CyberFlameWire(val tile: Int, val owner: Int, val shape: String, val msLeft: Long)

/** A power-up on [tile]; [kind] SPEED. */
@Serializable
data class CyberItemWire(val tile: Int, val kind: String, val msLeft: Long)

/**
 * [victim] lost a heart to [by] ([self]: their own bomb; [crushed]: the
 * closing arena); [victimPoints] / [byPoints] are what each scored for it.
 */
@Serializable
data class CyberHitWire(
    val victim: Int,
    val by: Int,
    val livesLeft: Int,
    val victimPoints: Int,
    val byPoints: Int,
    val self: Boolean = false,
    val crushed: Boolean = false,
)

/** Cyber Blast's match events (`MatchSnapshot.events`); `"type"` names each. */
@Serializable
sealed interface CyberBlastWireEvent {
    @Serializable @SerialName("CyberHit") data class Hit(val hit: CyberHitWire) : CyberBlastWireEvent
    /** [seat] has no hearts left. */
    @Serializable @SerialName("CyberOut") data class Out(val seat: Int) : CyberBlastWireEvent
}
