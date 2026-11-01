package com.kglabs28.yapnplay.backend.contract.doodleking

import kotlinx.serialization.Serializable

/**
 * Doodle King's `MatchSnapshot.publicState`, as one viewer may see it. The
 * word goes only to the drawer (and to everyone at the reveal); the others
 * get [hint]. The drawing itself is not here: strokes travel on their own
 * (`DoodleStroke`), and [drawingId] / [strokeCount] tell the app whether it
 * has them all (else it asks with `DoodleSync`).
 */
@Serializable
data class DoodleKingPublicState(
    /** INTRO, PICK, DRAW, REVEAL or FINISHED. */
    val phase: String,
    /** 1-based round, and how many there are. */
    val round: Int,
    val rounds: Int,
    /** The round's words: EASY, MEDIUM or HARD. */
    val level: String,
    /** Whose turn it is to draw. */
    val drawerSeat: Int,
    /** When the phase ends (server epoch millis). */
    val phaseEndsAt: Long,
    /** The drawing time of a turn (seconds), for the timer ring. */
    val drawSeconds: Int,
    /** The word: the drawer's own, everyone's at the reveal; null for the others. */
    val word: String? = null,
    /** The 3 words to pick from (the drawer, while picking); empty for everyone else. */
    val options: List<String> = emptyList(),
    /** The word as the guessers see it: `_` for a hidden letter, letters given as hints, spaces and hyphens as they are. */
    val hint: String = "",
    val seats: List<DoodleSeatState>,
    /** This turn's guesses and news, oldest first (cleared each turn); only what this viewer may see. */
    val feed: List<DoodleFeedEntry> = emptyList(),
    /** Which turn's drawing this is (changes every turn), and how many strokes it has. */
    val drawingId: Int = 0,
    val strokeCount: Int = 0,
)

@Serializable
data class DoodleSeatState(
    val seat: Int,
    val score: Int,
    /** Guessed this turn's word (guessers), or is drawing (false). */
    val guessed: Boolean = false,
    /** Points this turn so far (shown at the reveal). */
    val turnPoints: Int = 0,
    /** Left the match: no turns, kept on the leaderboard. */
    val removed: Boolean = false,
)

/**
 * One line of the turn's feed. [kind]: GUESS (a wrong guess, [text]), CLOSE
 * (only the guesser: [text] was nearly right), CORRECT ([seat] got it, +[points]),
 * CHAT ([text] from someone who already guessed; only they and the drawer see it),
 * PASSED (the drawer passed, [points] negative), NO_DRAWING (nothing drawn, [points]),
 * SKIPPED (the drawer was away), TIME_UP (nobody got it).
 */
@Serializable
data class DoodleFeedEntry(
    val kind: String,
    val seat: Int,
    val text: String? = null,
    val points: Int = 0,
)
