package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.shuffled

/**
 * Letter hints: a few letters show one by one in the later part of the
 * drawing time. Short words get none; never more than one per 3 letters,
 * and the last 2 hidden letters are never given.
 */
object DoodleKingHints {

    /** Which letters will show, in order (picked at random when the drawing starts). */
    fun order(word: String, random: GameRandom): List<Int> {
        val letters = word.indices.filter { word[it].isLetterOrDigit() }
        return letters.shuffled(random).take(maxHints(letters.size))
    }

    private fun maxHints(letters: Int): Int =
        if (letters <= NO_HINT_LETTERS) 0 else minOf(letters / LETTERS_PER_HINT, letters - HIDDEN_LETTERS_LEFT)

    /** How many of [total] hints show at [now]: none before [startPct] % of the time, then evenly spread to the end. */
    fun shownAt(now: Long, startedAt: Long, endsAt: Long, startPct: Int, total: Int): Int {
        if (total == 0 || endsAt <= startedAt) return 0
        val elapsed = (now - startedAt).toDouble() / (endsAt - startedAt)
        val start = startPct / PERCENT
        if (elapsed < start) return 0
        return ((elapsed - start) / (1 - start) * (total + 1)).toInt().coerceIn(0, total)
    }

    /** The word as guessers see it: `_` for hidden letters, given letters in capitals, spaces and hyphens as they are. */
    fun mask(word: String, shown: List<Int>): String =
        word.mapIndexed { i, c -> if (!c.isLetterOrDigit()) c else if (i in shown) c.uppercaseChar() else '_' }.joinToString("")

    private const val PERCENT = 100.0
}
