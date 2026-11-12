package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.application.match.games.doodleking.DoodleRecentWords
import com.kglabs28.yapnplay.backend.games.base.play.DefaultGameRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Words don't come back game after game: the whole list comes round before one shows again. */
class DoodleKingWordPickerTest {
    private val random = DefaultGameRandom()
    private val start = DoodleKingEngine().start(DoodleKingRules(), 4, random) as DoodleKingState
    private val easy = DoodleKingWords.pool(start.level, hinglish = false)

    @Test
    fun `words offered in earlier matches wait until every other word was offered`() {
        val recent = DoodleRecentWords()
        val seen = mutableListOf<String>()
        // One drawer a match, matches one after another, sharing the server's memory.
        repeat(easy.size / WORD_CHOICES) {
            val options = DoodleKingWordPicker.options(start.copy(stale = recent.oldestFirst()), random)
            assertEquals(WORD_CHOICES, options.size)
            assertTrue(options.none { it in seen }, "repeated before the list came round: $options")
            seen += options
            recent.offered(options)
        }
        // Once all were offered, the ones offered longest ago come first.
        val next = DoodleKingWordPicker.options(start.copy(stale = recent.oldestFirst()), random)
        val leftOver = easy - seen.toSet()
        val expected = (leftOver + seen).take(WORD_CHOICES).toSet()
        assertEquals(expected, next.toSet())
    }

    @Test
    fun `a word offered earlier in the match never comes back while others are left`() {
        val used = easy.drop(WORD_CHOICES).toSet()
        val options = DoodleKingWordPicker.options(start.copy(used = used, stale = easy), random)
        assertEquals(easy.take(WORD_CHOICES).toSet(), options.toSet())
    }
}
