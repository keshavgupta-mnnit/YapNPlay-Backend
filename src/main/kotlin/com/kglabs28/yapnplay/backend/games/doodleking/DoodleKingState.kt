package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.HouseRules
import com.kglabs28.yapnplay.backend.games.base.rules.MandatoryRules
import com.kglabs28.yapnplay.backend.games.base.rules.PlayMode
import kotlinx.serialization.Serializable

/** A turn: the drawer's intro, picking 1 of 3 words, drawing, then the word shown. LIMIT: the rounds are over, the table votes to keep playing. */
enum class DoodlePhase { INTRO, PICK, DRAW, REVEAL, LIMIT, FINISHED }

/**
 * Doodle King's whole state. Turns run on the game's own clock: every
 * phase ends at [phaseEndsAt] (0 until the first tick starts the match).
 * [turnIndex] walks seat 1, 2, … round after round; [drawer] is that seat.
 * [used] holds every word offered so far, so none comes back in the match.
 */
@Serializable
data class DoodleKingState(
    override val gameId: GameId,
    val dkRules: DoodleKingRules,
    val seatCount: Int,
    val scores: Map<Int, Int>,
    val phase: DoodlePhase = DoodlePhase.INTRO,
    val phaseEndsAt: Long = 0L,
    val turnIndex: Int = 0,
    val drawer: Int = 1,
    val options: List<String> = emptyList(),
    val word: String? = null,
    val used: Set<String> = emptySet(),
    /** Letter positions given as hints, in order; the first [hintsShown] are showing. */
    val hintOrder: List<Int> = emptyList(),
    val hintsShown: Int = 0,
    val drawStartedAt: Long = 0L,
    /** This turn's points by seat (the reveal shows them). */
    val turnPoints: Map<Int, Int> = emptyMap(),
    /** Seats that guessed this turn's word, first first. */
    val guessed: List<Int> = emptyList(),
    val removed: Set<Int> = emptySet(),
    /** Seats away right now (as of the last tick): their turn is skipped, the early end doesn't wait for them. */
    val absent: Set<Int> = emptySet(),
    val feed: List<DoodleFeedItem> = emptyList(),
    /** Changes every drawing turn: the canvas starts empty. */
    val drawingId: Int = 0,
    /** The drawer has really drawn something this turn (not just a dot). */
    val inked: Boolean = false,
    val passed: Boolean = false,
) : GameState {
    override val rules: GameRules get() = dkRules
    override val seats: List<SeatNumber> get() = (1..seatCount).filter { it !in removed }.map(::SeatNumber)
    override val turn: TurnState get() = DoodleTurnState(SeatNumber(drawer), phase.name)

    val round: Int get() = turnIndex / seatCount + 1
    val level: DoodleLevel get() = DoodleLevel.ofRound(round)
    val active: List<Int> get() = (1..seatCount).filter { it !in removed }

    /** Who should guess now: everyone still in and here, but the drawer. */
    val guessers: List<Int> get() = active.filter { it != drawer && it !in absent }

    /** [points] at this round's word level (harder words score more). */
    fun forLevel(points: Int): Int = points * (dkRules.mandatory.levelPct.getOrNull(level.ordinal) ?: PERCENT) / PERCENT

    private companion object {
        const val PERCENT = 100
    }
}

data class DoodleTurnState(override val seat: SeatNumber, override val phase: String) : TurnState

/**
 * One feed line (see the wire's DoodleFeedEntry for the kinds). [visibleTo]
 * null: everyone sees it; else only those seats (a "close" note, chat
 * among those who already guessed).
 */
@Serializable
data class DoodleFeedItem(
    val kind: DoodleFeedKind,
    val seat: Int,
    val text: String? = null,
    val points: Int = 0,
    val visibleTo: Set<Int>? = null,
)

enum class DoodleFeedKind { GUESS, CLOSE, CORRECT, CHAT, PASSED, NO_DRAWING, SKIPPED, TIME_UP }

/** Timing and points, fixed for the match when it starts (admin settings). */
@Serializable
data class DoodleKingMandatoryRules(
    val rounds: Int = 3,
    val introMs: Long = 3_000,
    val pickMs: Long = 10_000,
    val drawMs: Long = 60_000,
    val revealMs: Long = 5_000,
    val guessPoints: List<Int> = listOf(100, 80, 65, 50, 40, 30, 25, 20),
    /** Guessing and drawing points × this % by word level (easy, medium, hard); the pass penalty doesn't change. */
    val levelPct: List<Int> = listOf(100, 150, 200),
    val drawerPerGuess: Int = 25,
    val allGuessedBonus: Int = 30,
    val passPenalty: Int = 50,
    val hintStartPct: Int = 40,
) : MandatoryRules

/** A room's toggles: Hinglish words (Quick Play: English only). */
@Serializable
data class DoodleKingHouseRules(val hinglish: Boolean = false) : HouseRules

@Serializable
data class DoodleKingRules(
    override val mandatory: DoodleKingMandatoryRules = DoodleKingMandatoryRules(),
    override val house: DoodleKingHouseRules = DoodleKingHouseRules(),
) : GameRules {
    override fun isAllowed(mode: PlayMode): Boolean = true
}
