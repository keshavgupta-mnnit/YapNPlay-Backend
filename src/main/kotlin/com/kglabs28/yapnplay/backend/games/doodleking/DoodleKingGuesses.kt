package com.kglabs28.yapnplay.backend.games.doodleking

/** Matching a guess to the word: case, spaces and hyphens don't count; one or two letters off is "very close". */
object DoodleKingGuesses {

    fun normal(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    fun isRight(guess: String, word: String): Boolean = normal(guess).let { it.isNotEmpty() && it == normal(word) }

    fun isClose(guess: String, word: String): Boolean {
        val g = normal(guess)
        val w = normal(word)
        if (g.isEmpty() || g == w) return false
        val allowed = if (w.length >= LONG_WORD_LETTERS) 2 else 1
        return kotlin.math.abs(g.length - w.length) <= allowed && distance(g, w) <= allowed
    }

    /** Whether [text] gives the word away (a chat line from someone who already guessed). */
    fun givesAway(text: String, word: String): Boolean = normal(word).let { it.isNotEmpty() && it in normal(text) }

    /** Letters to add, drop or change to turn [a] into [b]. */
    private fun distance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                val change = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + change)
            }
            previous = current
        }
        return previous[b.length]
    }
}
