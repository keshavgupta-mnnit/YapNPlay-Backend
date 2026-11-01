package com.kglabs28.yapnplay.backend.application.match.games.doodleking

import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleDrawing
import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleStroke
import kotlin.math.hypot

/**
 * This turn's drawing as the server keeps it, for anyone who joins late or
 * misses strokes. Parts of a stroke join into one; every part is checked
 * (its turn, its size, points on the canvas). [inked]: real lines were
 * drawn (a dot or two doesn't count), so the drawer isn't "drawing nothing".
 */
class DoodleCanvas {
    var drawingId: Int = 0
        private set
    private val strokes = mutableListOf<DoodleStroke>()
    private var points = 0
    private var ink = 0.0

    val inked: Boolean get() = ink >= MIN_INK
    val strokeCount: Int get() = strokes.size

    /** A new turn's drawing: the canvas starts empty. */
    fun reset(id: Int) {
        drawingId = id
        strokes.clear()
        points = 0
        ink = 0.0
    }

    /** Adds a stroke part; false when it doesn't belong (another turn, off the canvas, too big). */
    fun add(part: DoodleStroke): Boolean {
        val valid = part.drawingId == drawingId && part.points.size % 2 == 0 && part.points.size <= MAX_PART_VALUES &&
            points + part.points.size <= MAX_DRAWING_VALUES && part.points.all { it in 0..CANVAS_MAX } &&
            part.colour in 0 until COLOURS && part.size in 0 until SIZES
        if (!valid) return false
        // A later part of the stroke being drawn joins it; anything else starts a new one.
        val ongoing = strokes.lastOrNull()?.takeIf { it.id == part.id }
        if (ongoing != null) strokes[strokes.lastIndex] = ongoing.copy(points = ongoing.points + part.points) else strokes += part
        if (!part.eraser) ink += length(ongoing?.points?.takeLast(2).orEmpty() + part.points)
        points += part.points.size
        return true
    }

    fun undo(id: Int): Boolean = (id == drawingId).also { if (it) strokes.removeLastOrNull() }

    fun clear(id: Int): Boolean = (id == drawingId).also { if (it) strokes.clear() }

    fun drawing(): DoodleDrawing = DoodleDrawing(drawingId, strokes.toList())

    /** Adds a whole stroke at once (a test bot's drawing). */
    fun addWhole(stroke: DoodleStroke) {
        strokes += stroke
        ink += length(stroke.points)
    }

    private fun length(values: List<Int>): Double =
        (2 until values.size step 2).sumOf { i -> hypot((values[i] - values[i - 2]).toDouble(), (values[i + 1] - values[i - 1]).toDouble()) }

    private companion object {
        /** Points are 0–4095 across the canvas. */
        const val CANVAS_MAX = 4_095
        const val COLOURS = 5
        const val SIZES = 3
        /** One part carries at most 300 points; a whole drawing at most 20 000. */
        const val MAX_PART_VALUES = 600
        const val MAX_DRAWING_VALUES = 40_000
        /** Line length that counts as having drawn something (about a quarter of the canvas width). */
        const val MIN_INK = 1_000.0
    }
}
