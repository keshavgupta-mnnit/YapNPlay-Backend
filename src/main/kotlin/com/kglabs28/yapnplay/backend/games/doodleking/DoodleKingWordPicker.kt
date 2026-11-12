package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.shuffled

/**
 * The drawer's 3 words: never one offered earlier in this match ([DoodleKingState.used]), and words
 * nobody was offered in earlier matches first ([DoodleKingState.stale] lists those, longest ago
 * first), so the whole list comes round before a word shows again. Pure: the randomness comes in.
 */
object DoodleKingWordPicker {

    fun options(s: DoodleKingState, random: GameRandom): List<String> {
        val level = DoodleKingWords.pool(s.level, s.dkRules.house.hinglish)
        val unused = level.filter { it !in s.used }
        // A very long match could run out of new words: then the level's words come round again.
        if (unused.isEmpty()) return level.shuffled(random).take(WORD_CHOICES)
        val age = s.stale.withIndex().associate { it.value to it.index }
        val fresh = unused.filter { it !in age }.shuffled(random)
        val oldest = unused.filter { it in age }.sortedBy { age.getValue(it) }
        // Shown in a random order, so the oldest word isn't always in the same place.
        return (fresh + oldest).take(WORD_CHOICES).shuffled(random)
    }
}
