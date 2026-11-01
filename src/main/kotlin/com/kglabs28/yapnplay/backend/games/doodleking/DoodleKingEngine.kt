package com.kglabs28.yapnplay.backend.games.doodleking

import com.kglabs28.yapnplay.backend.games.base.Game
import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.MatchOutcome
import com.kglabs28.yapnplay.backend.games.base.match.RankedSeat
import com.kglabs28.yapnplay.backend.games.base.match.SeatStatus
import com.kglabs28.yapnplay.backend.games.base.match.Viewer
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.base.play.TypedRejection
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.GameShowcase
import com.kglabs28.yapnplay.backend.games.base.rules.MatchmakingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TimingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TurnPolicy

/**
 * Doodle King: one player draws a word, the others race to guess it. Turns
 * run on the game's own clock ([DoodleTick]); guesses come from anyone at
 * once. Pure: the time comes in with each action. No CPU players outside
 * testing ([testBots], an admin setting read on every check).
 */
class DoodleKingEngine(private val testBots: () -> Boolean = { false }) : Game {
    override val id = GameId(DOODLE_KING_ID)
    override val displayName = DOODLE_KING_DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = DEFAULT_DRAW_SECONDS)
    override val matchmaking = MatchmakingConfig(seats = DOODLE_KING_QUICK_PLAY_SEATS, searchSeconds = DOODLE_KING_SEARCH_SECONDS)
    override val showcase = GameShowcase(taglines = DOODLE_KING_TAGLINES, artKey = DOODLE_KING_ID)
    override val houseRuleOptions = DOODLE_KING_HOUSE_RULES
    override val fixedRules = DOODLE_KING_FIXED_RULES
    override val minPlayers = DOODLE_KING_MIN_PLAYERS
    override val maxPlayers = DOODLE_KING_MAX_PLAYERS
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Simultaneous
    override val cpuFillAllowed: Boolean get() = testBots()
    /** Bots never act through the engine; the test bots live in the session. */
    override val bot: GameBot = object : GameBot {
        override fun chooseAction(state: GameState, seat: SeatNumber): Action? = null
    }

    override fun defaultRules(): GameRules = DoodleKingRules()

    override fun rulesSummary(): String =
        "Doodle King for 3–8 players: take turns drawing a word while the others guess. " +
            "3 rounds (easy, medium, hard words); the first to guess scores the most, the drawer scores for every right guess."

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        require(seatCount in 2..DOODLE_KING_MAX_PLAYERS) { "Doodle King needs 2-8 seats" }
        val dkRules = rules as? DoodleKingRules ?: DoodleKingRules()
        return DoodleKingState(gameId = id, dkRules = dkRules, seatCount = seatCount, scores = (1..seatCount).associateWith { 0 })
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as DoodleKingState
        if (s.phase == DoodlePhase.FINISHED) return reject("MATCH_ENDED", "Match ended")
        val turns = DoodleKingTurns(random)
        val me = seat.value
        val result: Pair<DoodleKingState, List<DoodleEvent>>? = when (action) {
            is DoodleTick -> turns.tick(s.copy(absent = action.away), action.now)
            is DoodlePick -> if (s.phase == DoodlePhase.PICK && me == s.drawer && action.index in s.options.indices) {
                turns.startDrawing(s, s.options[action.index], action.now) to listOf(DoodleUpdated)
            } else {
                return reject("ILLEGAL_ACTION", "Not your pick")
            }
            is DoodleGuess -> guess(turns, s, me, action) ?: return reject("ILLEGAL_ACTION", "Can't guess now")
            is DoodlePass -> if (s.phase == DoodlePhase.DRAW && me == s.drawer && !s.passed) {
                val penalty = s.dkRules.mandatory.passPenalty
                val passed = turns.addPoints(s, me, -penalty)
                    .copy(passed = true, feed = s.feed + DoodleFeedItem(DoodleFeedKind.PASSED, me, points = -penalty))
                turns.reveal(passed, action.now)
            } else {
                return reject("ILLEGAL_ACTION", "Can't pass now")
            }
            DoodleEnd -> turns.finish(s) to listOf(DoodleUpdated)
            is DoodleExtend -> if (s.phase == DoodlePhase.LIMIT) turns.extend(s, action.rounds, action.now) to listOf(DoodleUpdated) else return reject("ILLEGAL_ACTION", "Not at the limit")
            else -> return reject("BAD_REQUEST", "Unknown action for Doodle King")
        }
        return result?.let { (next, events) -> ApplyResult.Success(next, events) } ?: ApplyResult.Success(s, emptyList())
    }

    /**
     * A guess from [me]: right (points by order, the drawer scores too), very close (only they see it),
     * or wrong (everyone sees it). Once they have guessed, their lines are chat for the drawer and the
     * others who guessed, never giving the word away.
     */
    private fun guess(turns: DoodleKingTurns, s: DoodleKingState, me: Int, action: DoodleGuess): Pair<DoodleKingState, List<DoodleEvent>>? {
        val word = s.word ?: return null
        val text = action.text.trim().take(MAX_GUESS_LENGTH)
        if (s.phase != DoodlePhase.DRAW || me == s.drawer || me in s.removed || text.isEmpty()) return null
        val rules = s.dkRules.mandatory
        if (me in s.guessed) {
            if (DoodleKingGuesses.givesAway(text, word)) return null
            return s.withFeed(DoodleFeedItem(DoodleFeedKind.CHAT, me, text, visibleTo = s.guessed.toSet() + s.drawer)) to listOf(DoodleUpdated)
        }
        if (DoodleKingGuesses.isRight(text, word)) {
            val points = s.forLevel(rules.guessPoints.getOrElse(s.guessed.size) { rules.guessPoints.lastOrNull() ?: 0 })
            val scored = turns.addPoints(turns.addPoints(s, me, points), s.drawer, s.forLevel(rules.drawerPerGuess))
                .copy(guessed = s.guessed + me)
                .withFeed(DoodleFeedItem(DoodleFeedKind.CORRECT, me, points = points))
            val events = listOf(DoodleRightGuess(me, points))
            // Everyone got it: the word shows at once.
            return if (turns.everyoneGuessed(scored)) turns.reveal(scored, action.now).let { (r, e) -> r to events + e } else scored to events
        }
        val kind = if (DoodleKingGuesses.isClose(text, word)) DoodleFeedKind.CLOSE else DoodleFeedKind.GUESS
        return s.withFeed(DoodleFeedItem(kind, me, text, visibleTo = if (kind == DoodleFeedKind.CLOSE) setOf(me) else null)) to listOf(DoodleUpdated)
    }

    private fun DoodleKingState.withFeed(item: DoodleFeedItem) = copy(feed = (feed + item).takeLast(MAX_FEED))

    override fun currentActor(state: GameState) = SeatNumber((state as DoodleKingState).drawer)

    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult =
        reject("ILLEGAL_ACTION", "Doodle King has no autoplay")

    /** A player left: kept on the board; their own turn moves on, and under 2 players the match ends. */
    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as DoodleKingState
        if (s.phase == DoodlePhase.FINISHED) return s
        val left = s.copy(removed = s.removed + seat.value)
        return when {
            left.active.size < MIN_TO_PLAY -> left.copy(phase = DoodlePhase.FINISHED, options = emptyList())
            // The drawer left mid-turn: the turn ends on the next tick (no points either way).
            seat.value == left.drawer && left.phase != DoodlePhase.REVEAL -> left.copy(phase = DoodlePhase.REVEAL, phaseEndsAt = 1L, options = emptyList())
            else -> left
        }
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView = object : GameView {}

    override fun isFinished(state: GameState): Boolean = (state as DoodleKingState).phase == DoodlePhase.FINISHED

    override fun outcome(state: GameState): MatchOutcome {
        val s = state as DoodleKingState
        return MatchOutcome(ranking(s).mapIndexed { i, seat ->
            RankedSeat(SeatNumber(seat), if (seat in s.removed) SeatStatus.REMOVED else SeatStatus.FINISHED, i + 1)
        })
    }

    private fun reject(code: String, message: String) = ApplyResult.Rejected(TypedRejection(code, message))

    companion object {
        /** Seats by points (players who left last), ties by seat. */
        fun ranking(s: DoodleKingState): List<Int> =
            (1..s.seatCount).sortedWith(compareBy<Int>({ it in s.removed }, { -(s.scores[it] ?: 0) }, { it }))

        private const val DEFAULT_DRAW_SECONDS = 60L
        /** Fewer players than this still in: the match ends. */
        const val MIN_TO_PLAY = 2
    }
}
