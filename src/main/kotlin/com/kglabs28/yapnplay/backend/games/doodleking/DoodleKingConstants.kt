package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.rules.HouseRuleOption

const val DOODLE_KING_ID = "doodle-king"
const val DOODLE_KING_DISPLAY_NAME = "Doodle King"
val DOODLE_KING_TAGLINES = listOf("Draw", "Guess", "Rule")

/** Needs real people: 3 to 8 (no CPU players outside testing). Random tables seat 4. */
const val DOODLE_KING_MIN_PLAYERS = 3
const val DOODLE_KING_MAX_PLAYERS = 8
const val DOODLE_KING_QUICK_PLAY_SEATS = 4
const val DOODLE_KING_SEARCH_SECONDS = 30L

/** Room toggle: Hinglish words join the English ones (Quick Play is English only). */
const val HINGLISH_WORDS_KEY = "hinglish_words"

val DOODLE_KING_HOUSE_RULES = listOf(
    HouseRuleOption(HINGLISH_WORDS_KEY, "Hinglish Words", "Words like chai, patang and gol gappa join the English ones", true),
)

val DOODLE_KING_FIXED_RULES = listOf(
    "Rooms 3–8 players, random tables 4",
    "Everyone draws once a round: easy words, then medium, then hard",
    "The drawer picks 1 of 3 words and can't talk all turn",
    "Guess first for the most points; the drawer scores for every right guess",
    "Letters show up as hints near the end",
    "Pass, or draw nothing: −50",
)

/** Words offered to the drawer each turn. */
const val WORD_CHOICES = 3

/** A guess or chat line is cut to this many characters. */
const val MAX_GUESS_LENGTH = 40

/** The turn's feed keeps this many lines. */
const val MAX_FEED = 40

/** Words of up to this many letters get no letter hints. */
const val NO_HINT_LETTERS = 3

/** At most one hint per this many letters, and never the last [HIDDEN_LETTERS_LEFT]. */
const val LETTERS_PER_HINT = 3
const val HIDDEN_LETTERS_LEFT = 2

/** A guess this close (letters off) to the word counts as "very close": 1, or 2 for words this long. */
const val LONG_WORD_LETTERS = 8
