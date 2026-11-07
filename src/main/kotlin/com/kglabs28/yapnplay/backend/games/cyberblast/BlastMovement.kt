package com.kglabs28.yapnplay.backend.games.cyberblast

import kotlin.math.abs
import kotlin.math.sign

/**
 * Walking on the grid. Players move along rows and columns, from tile
 * centre to tile centre. Pushing across a corridor while a little off its
 * line slides them onto it first, and pushing into a pillar's corner slides
 * them round it (as in the old handheld games), so turns never feel sticky.
 */
object BlastMovement {

    /**
     * [player] walks [distance] (thousandths of a tile) the way they hold. Pushing between two ways
     * (a diagonal on the pad), the main one goes first; when it is blocked they go the side way
     * ([BlastPlayer.heldAlt]), so turning into a corridor never needs an exact push.
     */
    fun walk(state: CyberBlastState, player: BlastPlayer, distance: Int): BlastPlayer {
        val main = player.held ?: return player
        val moved = walkTowards(state, player, main, distance)
        val alt = player.heldAlt
        if (alt == null || moved.x != player.x || moved.y != player.y) return moved
        return walkTowards(state, player, alt, distance)
    }

    private fun walkTowards(state: CyberBlastState, player: BlastPlayer, dir: BlastDir, distance: Int): BlastPlayer {
        var x = player.x
        var y = player.y
        var left = distance
        // A few small moves: to the line, to the next centre, on to the one after.
        repeat(MAX_PARTS) {
            if (left <= 0) return@repeat
            val tile = BlastGrid.tileAt(x, y)
            val off = if (dir.horizontal) y - BlastGrid.centreY(tile) else x - BlastGrid.centreX(tile)
            val ahead = BlastGrid.next(state, tile, dir)
            val aheadOpen = ahead != null && BlastGrid.canEnter(state, ahead, tile)
            if (off != 0) {
                val toward = when {
                    aheadOpen -> -off.sign
                    // Round the corner: the neighbouring line is open ahead.
                    cornerOpen(state, tile, dir, off.sign) -> off.sign
                    else -> return@repeat
                }
                val room = if (toward == -off.sign) abs(off) else BLAST_TILE - abs(off)
                val step = minOf(left, room)
                if (dir.horizontal) y += toward * step else x += toward * step
                left -= step
                return@repeat
            }
            val pos = if (dir.horizontal) x else y
            val centre = if (dir.horizontal) BlastGrid.centreX(tile) else BlastGrid.centreY(tile)
            val sign = if (dir.horizontal) dir.dx else dir.dy
            val beforeCentre = (centre - pos) * sign
            val step = when {
                beforeCentre > 0 -> minOf(left, beforeCentre)
                aheadOpen -> minOf(left, BLAST_TILE + beforeCentre)
                else -> return@repeat
            }
            if (dir.horizontal) x += sign * step else y += sign * step
            left -= step
        }
        return player.copy(x = x, y = y, facing = dir)
    }

    /** Off the line towards [side]: that line's tile beside the player and its tile ahead are both open. */
    private fun cornerOpen(state: CyberBlastState, tile: Int, dir: BlastDir, side: Int): Boolean {
        val sideDir = when {
            dir.horizontal && side > 0 -> BlastDir.DOWN
            dir.horizontal -> BlastDir.UP
            side > 0 -> BlastDir.RIGHT
            else -> BlastDir.LEFT
        }
        val beside = BlastGrid.next(state, tile, sideDir) ?: return false
        val besideAhead = BlastGrid.next(state, beside, dir) ?: return false
        return BlastGrid.canEnter(state, beside, tile) && BlastGrid.canEnter(state, besideAhead, beside)
    }

    private const val MAX_PARTS = 4
}
