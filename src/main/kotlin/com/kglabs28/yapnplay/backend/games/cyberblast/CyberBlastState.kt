package com.kglabs28.yapnplay.backend.games.cyberblast

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import kotlinx.serialization.Serializable

/** COUNTDOWN: "3, 2, 1" (nobody moves); PLAYING; FINISHED: one player (or none) has hearts left. */
enum class BlastPhase { COUNTDOWN, PLAYING, FINISHED }

/**
 * The whole Cyber Blast board. [time] is the match's own clock (ms since
 * the countdown began): it only runs while the match plays, so a pause
 * (an end-game vote, a restart) never makes bombs go off early. Tiles are
 * `row * BlastGrid.STRIDE + col`; pillars are fixed by [BlastGrid], [walls] are the
 * tiles the closing arena has filled.
 */
@Serializable
data class CyberBlastState(
    override val gameId: GameId,
    val blastRules: CyberBlastRules,
    val seatCount: Int,
    /** The board's size in tiles (a Big Arena is bigger). */
    val cols: Int = BLAST_COLS,
    val rows: Int = BLAST_ROWS,
    val players: List<BlastPlayer>,
    val crates: Set<Int>,
    val walls: Set<Int> = emptySet(),
    val bombs: List<BlastBomb> = emptyList(),
    val flames: List<BlastFlame> = emptyList(),
    val items: List<BlastItem> = emptyList(),
    val time: Long = 0L,
    val phase: BlastPhase = BlastPhase.COUNTDOWN,
    val nextItemAt: Long = 0L,
    val nextCloseAt: Long = 0L,
    /** How many tiles the closing arena has filled, in its spiral order. */
    val closed: Int = 0,
    /** Seats in the order they lost their last heart (or left). */
    val outOrder: List<Int> = emptyList(),
    val removed: Set<Int> = emptySet(),
    val nextBombId: Int = 1,
) : GameState {
    override val rules: GameRules get() = blastRules
    override val seats: List<SeatNumber> get() = (1..seatCount).filter { it !in removed }.map(::SeatNumber)
    override val turn: TurnState get() = BlastTurnState(SeatNumber(alive.firstOrNull()?.seat ?: 1), phase.name)

    val mandatory: CyberBlastMandatoryRules get() = blastRules.mandatory

    /** Players still on the board: hearts left and still at the table. */
    val alive: List<BlastPlayer> get() = players.filter { it.inPlay }

    fun player(seat: Int): BlastPlayer? = players.firstOrNull { it.seat == seat }

    fun bombAt(tile: Int): BlastBomb? = bombs.firstOrNull { it.tile == tile }

    fun bombsOf(seat: Int): Int = bombs.count { it.owner == seat }
}

data class BlastTurnState(override val seat: SeatNumber, override val phase: String) : TurnState

/**
 * One player. [x], [y]: where they stand (thousandths of a tile). [held]: the
 * direction they're pushing (null: standing still); [facing]: where they last
 * walked, for the drawing.
 */
@Serializable
data class BlastPlayer(
    val seat: Int,
    val x: Int,
    val y: Int,
    val lives: Int,
    val radius: Int,
    val held: BlastDir? = null,
    /** The side way they also push (a diagonal on the pad): taken when [held] is blocked. */
    val heldAlt: BlastDir? = null,
    val facing: BlastDir = BlastDir.DOWN,
    val score: Int = 0,
    val boostUntil: Long = 0L,
    val shieldUntil: Long = 0L,
    /** Players hit in a row since this one last lost a heart: their blast reaches 1 tile more for each. */
    val streak: Int = 0,
    val hits: Int = 0,
    val removed: Boolean = false,
) {
    val inPlay: Boolean get() = lives > 0 && !removed
    val tile: Int get() = BlastGrid.tileAt(x, y)

    fun boosted(now: Long): Boolean = now < boostUntil
    fun shielded(now: Long): Boolean = now < shieldUntil
}

/** A bomb on [tile], [owner]'s, going off at [explodesAt] with the owner's reach when it was dropped. */
@Serializable
data class BlastBomb(val id: Int, val tile: Int, val owner: Int, val radius: Int, val explodesAt: Long)

/** How a flame tile is drawn: the blast's centre, or part of a horizontal or vertical arm. */
enum class FlameShape { CENTRE, HORIZONTAL, VERTICAL }

/** Fire on [tile] from [owner]'s bomb until [endsAt]. */
@Serializable
data class BlastFlame(val tile: Int, val owner: Int, val shape: FlameShape, val endsAt: Long)

enum class BlastItemKind { SPEED }

/** A power-up lying on [tile] until [expiresAt]. */
@Serializable
data class BlastItem(val tile: Int, val kind: BlastItemKind, val expiresAt: Long)
