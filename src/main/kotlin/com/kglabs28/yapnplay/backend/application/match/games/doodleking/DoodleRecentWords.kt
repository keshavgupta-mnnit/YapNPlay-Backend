package com.kglabs28.yapnplay.backend.application.match.games.doodleking

/**
 * Which Doodle King words this server has offered, longest ago first, across all matches: a new
 * match starts with them as its stale words, so players don't get the same words game after game.
 * Kept in memory (a restart starts the rotation afresh); safe to use from many matches at once.
 */
class DoodleRecentWords {
    // Insertion order is the age: offering a word again moves it to the end.
    private val offered = LinkedHashSet<String>()

    @Synchronized
    fun oldestFirst(): List<String> = offered.toList()

    @Synchronized
    fun offered(words: List<String>) {
        words.forEach { offered.remove(it); offered.add(it) }
    }
}
