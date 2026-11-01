package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.shuffled

/**
 * Moving a match through its turns: intro, pick, draw, reveal, next
 * drawer. Pure: the time comes in with each call.
 */
class DoodleKingTurns(private val random: GameRandom) {

    /** The game's clock at [now]: a phase that ran out moves on, hints show. Null when nothing changed. */
    fun tick(s: DoodleKingState, now: Long): Pair<DoodleKingState, List<DoodleEvent>>? = when {
        s.phase == DoodlePhase.FINISHED -> null
        s.phaseEndsAt == 0L -> begin(s, s.turnIndex, now) to listOf(DoodleUpdated)
        now >= s.phaseEndsAt -> when (s.phase) {
            DoodlePhase.INTRO -> afterIntro(s, now) to listOf(DoodleUpdated)
            DoodlePhase.PICK -> startDrawing(s, s.options[random.nextInt(s.options.size)], now) to listOf(DoodleUpdated)
            DoodlePhase.DRAW -> reveal(s, now)
            DoodlePhase.REVEAL -> begin(s, s.turnIndex + 1, now) to listOf(DoodleUpdated)
            // The keep-playing vote runs on the match's clock, not the game's.
            DoodlePhase.LIMIT, DoodlePhase.FINISHED -> null
        }
        s.phase == DoodlePhase.DRAW -> hints(s, now)?.let { it to listOf(DoodleUpdated) }
        else -> null
    }

    /**
     * The turn at [index] (or the next seat still in): its intro. Under 2 players left the match ends;
     * past the last round the table votes whether to keep playing.
     */
    fun begin(s: DoodleKingState, index: Int, now: Long): DoodleKingState {
        val last = s.seatCount * s.dkRules.mandatory.rounds
        val next = (index until last).firstOrNull { (it % s.seatCount + 1) !in s.removed }
        if (s.active.size < DoodleKingEngine.MIN_TO_PLAY) return finish(s)
        if (next == null) return s.copy(phase = DoodlePhase.LIMIT, turnIndex = last - 1, options = emptyList())
        val rules = s.dkRules.mandatory
        return s.copy(
            phase = DoodlePhase.INTRO,
            phaseEndsAt = now + rules.introMs,
            turnIndex = next,
            drawer = next % s.seatCount + 1,
            options = emptyList(),
            word = null,
            hintOrder = emptyList(),
            hintsShown = 0,
            turnPoints = emptyMap(),
            guessed = emptyList(),
            feed = emptyList(),
            inked = false,
            passed = false,
        )
    }

    fun finish(s: DoodleKingState): DoodleKingState = s.copy(phase = DoodlePhase.FINISHED, options = emptyList())

    /** [rounds] more rounds, from the next turn. */
    fun extend(s: DoodleKingState, rounds: Int, now: Long): DoodleKingState {
        val more = s.copy(dkRules = s.dkRules.copy(mandatory = s.dkRules.mandatory.copy(rounds = s.dkRules.mandatory.rounds + rounds)))
        return begin(more, s.seatCount * s.dkRules.mandatory.rounds, now)
    }

    /** The intro is over: the drawer picks a word, or is skipped when away. */
    private fun afterIntro(s: DoodleKingState, now: Long): DoodleKingState {
        // The next turn's feed opens with the note, so everyone sees why the turn moved on.
        if (s.drawer in s.absent) return begin(s, s.turnIndex + 1, now).copy(feed = listOf(DoodleFeedItem(DoodleFeedKind.SKIPPED, s.drawer)))
        val pool = DoodleKingWords.pool(s.level, s.dkRules.house.hinglish).filter { it !in s.used }
        // A very long match could run out of new words: then the level's words come round again.
        val options = (pool.ifEmpty { DoodleKingWords.pool(s.level, s.dkRules.house.hinglish) }).shuffled(random).take(WORD_CHOICES)
        return s.copy(
            phase = DoodlePhase.PICK,
            phaseEndsAt = now + s.dkRules.mandatory.pickMs,
            options = options,
            // None of the 3 comes back later: the drawer has seen them all.
            used = s.used + options,
        )
    }

    fun startDrawing(s: DoodleKingState, word: String, now: Long): DoodleKingState = s.copy(
        phase = DoodlePhase.DRAW,
        phaseEndsAt = now + s.dkRules.mandatory.drawMs,
        drawStartedAt = now,
        options = emptyList(),
        word = word,
        hintOrder = DoodleKingHints.order(word, random),
        hintsShown = 0,
        drawingId = s.drawingId + 1,
    )

    private fun hints(s: DoodleKingState, now: Long): DoodleKingState? {
        val shown = DoodleKingHints.shownAt(now, s.drawStartedAt, s.phaseEndsAt, s.dkRules.mandatory.hintStartPct, s.hintOrder.size)
        return if (shown > s.hintsShown) s.copy(hintsShown = shown) else null
    }

    /**
     * The drawing time is over (or everyone guessed, or the drawer passed): the word
     * shows. The drawer scores a bonus when everyone got it, and loses points for
     * drawing nothing; nobody guessing a real drawing costs them nothing.
     */
    fun reveal(s: DoodleKingState, now: Long): Pair<DoodleKingState, List<DoodleEvent>> {
        val rules = s.dkRules.mandatory
        val note: DoodleFeedItem?
        val drawerPoints: Int
        when {
            s.passed -> { note = null; drawerPoints = 0 }
            !s.inked -> { note = DoodleFeedItem(DoodleFeedKind.NO_DRAWING, s.drawer, points = -rules.passPenalty); drawerPoints = -rules.passPenalty }
            s.guessed.isEmpty() -> { note = DoodleFeedItem(DoodleFeedKind.TIME_UP, s.drawer); drawerPoints = 0 }
            everyoneGuessed(s) -> { note = null; drawerPoints = s.forLevel(rules.allGuessedBonus) }
            else -> { note = null; drawerPoints = 0 }
        }
        val scored = addPoints(s, s.drawer, drawerPoints)
        val revealed = scored.copy(
            phase = DoodlePhase.REVEAL,
            phaseEndsAt = now + rules.revealMs,
            feed = scored.feed + listOfNotNull(note),
        )
        return revealed to listOf(DoodleTurnEnded(s.word.orEmpty(), revealed.turnPoints))
    }

    fun everyoneGuessed(s: DoodleKingState): Boolean = s.guessers.isNotEmpty() && s.guessers.all { it in s.guessed }

    fun addPoints(s: DoodleKingState, seat: Int, points: Int): DoodleKingState = if (points == 0) s else s.copy(
        scores = s.scores + (seat to (s.scores[seat] ?: 0) + points),
        turnPoints = s.turnPoints + (seat to (s.turnPoints[seat] ?: 0) + points),
    )
}
