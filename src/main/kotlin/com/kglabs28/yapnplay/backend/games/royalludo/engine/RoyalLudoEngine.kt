package com.kglabs28.yapnplay.backend.games.royalludo.engine

import com.kglabs28.yapnplay.backend.games.base.Game
import com.kglabs28.yapnplay.backend.games.base.identity.*
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.MovePiece
import com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics.PieceId
import com.kglabs28.yapnplay.backend.games.base.match.*
import com.kglabs28.yapnplay.backend.games.base.play.*
import com.kglabs28.yapnplay.backend.games.base.rules.*
import com.kglabs28.yapnplay.backend.games.royalludo.utils.*
import kotlinx.serialization.Serializable

class RoyalLudoEngine : Game {
    override val id = GameId(ROYAL_LUDO_ID)
    override val displayName = ROYAL_LUDO_DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = ROYAL_LUDO_TURN_SECONDS)
    override val matchmaking = MatchmakingConfig(
        seats = ROYAL_LUDO_QUICK_PLAY_SEATS,
        searchSeconds = ROYAL_LUDO_SEARCH_SECONDS,
    )
    override val showcase = GameShowcase(taglines = ROYAL_LUDO_TAGLINES, artKey = ROYAL_LUDO_ID)
    override val houseRuleOptions = ROYAL_LUDO_HOUSE_RULES
    override val fixedRules = ROYAL_LUDO_FIXED_RULES
    override val minPlayers = ROYAL_LUDO_PLAYERS
    override val maxPlayers = ROYAL_LUDO_PLAYERS
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Sequential
    override val bot = RoyalLudoBot()

    override fun defaultRules(): GameRules = RoyalLudoRules.TRADITIONAL

    override fun rulesSummary(): String =
        "Traditional Royal Ludo for 4 players: leave base on a 6, extra turn " +
            "on a 6, a capture or a finished piece, three sixes forfeit, exact " +
            "roll to finish, lone pieces are captured except on safe squares, " +
            "pairs of two or more same-colour pieces are protected."

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        require(seatCount in 2..4) { "Royal Ludo requires 2-4 players" }
        val ludoRules = rules as? RoyalLudoRules ?: RoyalLudoRules.TRADITIONAL
        // Seats take colours in board order (clockwise from a random
        // colour), so turns go round the board; two players sit opposite.
        val first = random.nextInt(COLOUR_COUNT)
        val step = if (seatCount == 2) COLOUR_COUNT / 2 else 1
        val seatColours = (1..seatCount).associateWith { (first + (it - 1) * step) % COLOUR_COUNT }
        val pieces = (1..seatCount).associateWith { List(ludoRules.mandatory.piecesPerColour) { PROGRESS_IN_BASE } }
        val seats = (1..seatCount).map { SeatNumber(it) }
        return RoyalLudoState(
            _gameId = id, _rules = ludoRules, _seats = seats,
            _turn = RoyalLudoTurnState(SeatNumber(1), Phase.AWAITING_ROLL.name),
            seatColours = seatColours, pieces = pieces, currentSeat = 1, phase = Phase.AWAITING_ROLL,
        )
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as RoyalLudoState
        if (s.phase == Phase.FINISHED) return ApplyResult.Rejected(TypedRejection("MATCH_ENDED", "Match ended"))
        if (seat.value != s.currentSeat) return ApplyResult.Rejected(TypedRejection("NOT_YOUR_TURN", "Not your turn"))
        return when (action) {
            is RollDice -> handleRoll(s, random)
            is MovePiece -> handleMove(s, action.pieceId.value.toInt())
            else -> ApplyResult.Rejected(TypedRejection("BAD_REQUEST", "Unknown action for Royal Ludo"))
        }
    }

    override fun currentActor(state: GameState) = SeatNumber((state as RoyalLudoState).currentSeat)

    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult = autoplayWith(bot, state, seat, random)

    /** Plays [seat]'s step with [player] (its values come from the runtime settings). */
    fun autoplayWith(player: RoyalLudoBot, state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val s = state as RoyalLudoState
        if (s.phase == Phase.FINISHED) return ApplyResult.Rejected(TypedRejection("MATCH_ENDED", "Match ended"))
        if (seat.value != s.currentSeat) return ApplyResult.Rejected(TypedRejection("NOT_YOUR_TURN", "Not your turn"))
        return when (s.phase) {
            // Same fair, independent roll as a player's own.
            Phase.AWAITING_ROLL -> handleRoll(s, random)
            Phase.AWAITING_MOVE -> player.bestPiece(s)?.let { handleMove(s, it) }
                ?: ApplyResult.Rejected(TypedRejection("NO_LEGAL_MOVE", "No legal move"))
            Phase.FINISHED -> ApplyResult.Rejected(TypedRejection("MATCH_ENDED", "Match ended"))
        }
    }

    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as RoyalLudoState
        if (seat.value !in s.seats.map { it.value }) return s
        val newSeats = s.seats.filter { it.value != seat.value }
        val newRemovalOrder = s.removalOrder + seat.value
        var newCurrentSeat = s.currentSeat
        if (seat.value == s.currentSeat) {
            newSeats.firstOrNull()?.let { newCurrentSeat = it.value } ?: return s.copy(
                seats = newSeats, removalOrder = newRemovalOrder, phase = Phase.FINISHED,
                turn = RoyalLudoTurnState(SeatNumber(1), Phase.FINISHED.name),
            )
        }
        return s.copy(
            seats = newSeats, removalOrder = newRemovalOrder, currentSeat = newCurrentSeat,
            phase = if (newSeats.size <= 1) Phase.FINISHED else s.phase,
            turn = RoyalLudoTurnState(SeatNumber(newCurrentSeat), s.phase.name),
        )
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView {
        val s = state as RoyalLudoState
        val seatViews = s.seats.map { sn ->
            val seat = sn.value
            RoyalLudoSeatView(
                seat = seat, colour = s.colourForSeat(seat), pieces = s.piecesForSeat(seat),
                status = when {
                    seat in s.removalOrder -> "REMOVED"
                    seat in s.finishingOrder -> "FINISHED"
                    else -> "ACTIVE"
                },
                finished = seat in s.finishingOrder,
            )
        }
        val legalMoves = when (viewer) {
            is Viewer.Player -> if (viewer.seat.value == s.currentSeat && s.phase == Phase.AWAITING_MOVE) s.legalPieces else emptyList()
            Viewer.Spectator -> emptyList()
        }
        return RoyalLudoView(
            seats = seatViews, currentSeat = s.currentSeat, phase = s.phase.name,
            lastRoll = s.lastRoll, legalMoves = legalMoves,
            finishingOrder = s.finishingOrder, turnCounter = s.turnCounter,
        )
    }

    override fun isFinished(state: GameState) = (state as RoyalLudoState).phase == Phase.FINISHED

    override fun outcome(state: GameState): MatchOutcome {
        val s = state as RoyalLudoState
        val ranking = mutableListOf<RankedSeat>()
        for ((index, seat) in s.finishingOrder.withIndex())
            ranking.add(RankedSeat(SeatNumber(seat), SeatStatus.FINISHED, index + 1))
        // Not finished (an early end): the furthest along first.
        val unfinished = s.seats.map { it.value }.filter { it !in s.finishingOrder }
            .sortedByDescending { seat -> s.piecesForSeat(seat).sumOf { it.coerceAtLeast(0) } }
        for (seat in unfinished)
            ranking.add(RankedSeat(SeatNumber(seat), SeatStatus.FINISHED, ranking.size + 1))
        for (seat in s.removalOrder.reversed())
            ranking.add(RankedSeat(SeatNumber(seat), SeatStatus.REMOVED, ranking.size + 1))
        return MatchOutcome(ranking)
    }

    private fun handleRoll(rolling: RoyalLudoState, random: GameRandom): ApplyResult {
        // Stuck in base: each roll in a row without a six makes the next six a little likelier (same for every
        // seat, bots too), never certain. A fair roll otherwise.
        val stuck = rolling.piecesForSeat(rolling.currentSeat).let { pieces -> PROGRESS_IN_BASE in pieces && pieces.none { it in 0 until PROGRESS_FINISH } }
        val misses = rolling.baseMisses[rolling.currentSeat] ?: 0
        val roll = if (stuck && misses > 0 && rolling.sixBoostPerMissPct > 0) boostedRoll(misses, rolling, random) else random.nextInt(6) + 1
        val state = rolling.copy(baseMisses = rolling.baseMisses + (rolling.currentSeat to if (stuck && roll != SIX) misses + 1 else 0))
        val events = mutableListOf<GameEvent>()
        if (state.ludoRules.house.threeSixesForfeit && roll == 6 && state.consecutiveSixes >= MAX_CONSECUTIVE_SIXES - 1) {
            // Third six: the whole turn is void, every piece goes back to
            // where it stood when this turn began.
            events.add(DiceRolled(state.currentSeat, roll))
            events.add(TurnForfeited(state.currentSeat, "THREE_SIXES"))
            events.add(TurnReverted(state.currentSeat, state.turnStartPieces))
            val newState = state.copy(pieces = state.turnStartPieces, consecutiveSixes = 0, lastRoll = roll, phase = Phase.AWAITING_ROLL, legalPieces = emptyList(), turnCounter = state.turnCounter + 1)
            return ApplyResult.Success(advanceTurn(newState, events), events)
        }
        val newConsecutiveSixes = if (roll == 6) state.consecutiveSixes + 1 else 0
        val legalPieces = computeLegalPieces(state, roll)
        events.add(DiceRolled(state.currentSeat, roll))
        val newState = state.copy(lastRoll = roll, legalPieces = legalPieces, consecutiveSixes = newConsecutiveSixes, phase = if (legalPieces.isNotEmpty()) Phase.AWAITING_MOVE else Phase.AWAITING_ROLL, turnCounter = state.turnCounter + 1)
        if (legalPieces.isEmpty()) {
            events.add(TurnPassed(state.currentSeat, "NO_LEGAL_MOVE"))
            return ApplyResult.Success(advanceTurn(newState, events), events)
        }
        if (state.ludoRules.house.autoMoveWhenSingleOption && legalPieces.size == 1) {
            // Keep the roll in front of the automatic move, so every client shows the dice first.
            val moved = handleMove(newState, legalPieces.first())
            return if (moved is ApplyResult.Success) ApplyResult.Success(moved.state, events + moved.events) else moved
        }
        return ApplyResult.Success(newState, events)
    }

    /**
     * A six with chance 1/6 + [misses] × the boost (in hundredths of a percent, capped);
     * otherwise 1–5, each equally likely.
     */
    private fun boostedRoll(misses: Int, s: RoyalLudoState, random: GameRandom): Int {
        val chance = minOf(FAIR_SIX_BASIS + misses * s.sixBoostPerMissPct * PCT_TO_BASIS, maxOf(s.sixChanceCapPct * PCT_TO_BASIS, FAIR_SIX_BASIS))
        return if (random.nextInt(BASIS) < chance) SIX else random.nextInt(SIX - 1) + 1
    }

    private fun handleMove(state: RoyalLudoState, piece: Int): ApplyResult {
        val s = state
        val events = mutableListOf<GameEvent>()
        if (piece !in s.legalPieces) return ApplyResult.Rejected(TypedRejection("ILLEGAL_MOVE", "Piece $piece cannot move"))
        val colour = s.colourForSeat(s.currentSeat)
        val startSquare = RoyalLudoBoardUtils.startSquareFor(colour)
        val currentProgress = s.pieceProgress(s.currentSeat, piece)
        val roll = s.lastRoll ?: return ApplyResult.Rejected(TypedRejection("BAD_REQUEST", "No roll"))

        val newProgress: Int
        val squaresPassed: Int
        when {
            currentProgress == PROGRESS_IN_BASE -> {
                if (roll !in s.ludoRules.house.baseExitRolls) return ApplyResult.Rejected(TypedRejection("ILLEGAL_MOVE", "Need ${s.ludoRules.house.baseExitRolls} to leave base"))
                newProgress = 0; squaresPassed = 0
            }
            currentProgress + roll == PROGRESS_FINISH -> { newProgress = PROGRESS_FINISH; squaresPassed = roll }
            s.ludoRules.mandatory.exactFinish && currentProgress + roll > PROGRESS_FINISH -> return ApplyResult.Rejected(TypedRejection("ILLEGAL_MOVE", "Need exact roll"))
            currentProgress + roll <= PROGRESS_FINISH -> { newProgress = currentProgress + roll; squaresPassed = roll }
            else -> return ApplyResult.Rejected(TypedRejection("ILLEGAL_MOVE", "Exceeds finish"))
        }

        val newPieces = s.pieces.toMutableMap()
        val seatPieces = newPieces[s.currentSeat]!!.toMutableList()
        seatPieces[piece] = newProgress
        newPieces[s.currentSeat] = seatPieces

        events.add(PieceMoved(s.currentSeat, piece, currentProgress, newProgress, squaresPassed))

        var captured = false
        if (s.ludoRules.mandatory.capturesSendToBase && newProgress in 0..PROGRESS_ENTER_HOME) {
            val absSquare = RoyalLudoBoardUtils.absoluteSquare(startSquare, newProgress)
            if (!RoyalLudoBoardUtils.isSafeSquare(absSquare)) {
                val opposingSeat = findOpposingPiece(s, absSquare, colour)
                if (opposingSeat != null) {
                    val opposingColour = s.colourForSeat(opposingSeat)
                    val opposingCount = countPiecesOnSquare(s, absSquare, opposingColour)
                    // Protected pair (2+) is never captured; with pair
                    // protection off, one piece of the group is captured.
                    val canCapture = if (s.ludoRules.house.pairProtection) opposingCount == 1 else opposingCount >= 1
                    if (canCapture) {
                        val victimPiece = findPieceOnSquare(s, absSquare, opposingSeat)
                        val victimPieces = newPieces[opposingSeat]!!.toMutableList()
                        victimPieces[victimPiece] = PROGRESS_IN_BASE
                        newPieces[opposingSeat] = victimPieces
                        events.add(PieceCaptured(s.currentSeat, opposingSeat, victimPiece, "CAPTURE"))
                        captured = true
                    }
                }
            }
        }

        val finished = newProgress == PROGRESS_FINISH
        if (finished) events.add(PieceFinished(s.currentSeat, piece))

        val allFinished = newPieces[s.currentSeat]!!.all { it == PROGRESS_FINISH }
        val extraTurn = when {
            // All pieces home: this seat is done, no extra turn.
            allFinished -> false
            captured && s.ludoRules.house.extraTurnOnCapture -> true
            finished && s.ludoRules.house.extraTurnOnHome -> true
            roll == 6 && s.ludoRules.house.extraTurnOnSix -> true
            else -> false
        }
        if (extraTurn) events.add(ExtraTurn(s.currentSeat, when { captured -> "CAPTURE"; finished -> "HOME"; else -> "SIX" }))

        val newFinishingOrder = if (allFinished && s.currentSeat !in s.finishingOrder) {
            events.add(SeatFinished(s.currentSeat, s.finishingOrder.size + 1))
            s.finishingOrder + s.currentSeat
        } else s.finishingOrder

        val activeSeats = s.seats.map { it.value }.filter { it !in newFinishingOrder }
        // Second-to-last finished: the one left takes last place and the match ends.
        val matchOver = activeSeats.size <= 1

        val nextSeat = if (!extraTurn && !matchOver) nextActiveSeat(s, newFinishingOrder) else s.currentSeat
        val newState = s.copy(
            pieces = newPieces, lastRoll = null,
            legalPieces = if (extraTurn) emptyList() else s.legalPieces,
            consecutiveSixes = if (roll == 6 && !extraTurn) 0 else s.consecutiveSixes,
            finishingOrder = newFinishingOrder, phase = if (matchOver) Phase.FINISHED else Phase.AWAITING_ROLL,
            turnCounter = s.turnCounter + 1,
            turn = RoyalLudoTurnState(SeatNumber(nextSeat), if (matchOver) Phase.FINISHED.name else Phase.AWAITING_ROLL.name),
            currentSeat = nextSeat,
            // A new seat's turn starts from this board.
            turnStartPieces = if (nextSeat != s.currentSeat) newPieces else s.turnStartPieces,
        )
        val finalEvents = if (matchOver) events + MatchEnded(newFinishingOrder + activeSeats + s.removalOrder.reversed()) else events
        return ApplyResult.Success(newState, finalEvents)
    }

    private fun computeLegalPieces(state: RoyalLudoState, roll: Int): List<Int> {
        val legal = mutableListOf<Int>()
        for (piece in 0 until state.ludoRules.mandatory.piecesPerColour) {
            val progress = state.pieceProgress(state.currentSeat, piece)
            when {
                progress == PROGRESS_IN_BASE -> if (roll in state.ludoRules.house.baseExitRolls) legal.add(piece)
                progress == PROGRESS_FINISHED -> {}
                progress + roll == PROGRESS_FINISH -> legal.add(piece)
                state.ludoRules.mandatory.exactFinish && progress + roll > PROGRESS_FINISH -> {}
                progress + roll < PROGRESS_FINISH -> legal.add(piece)
            }
        }
        return legal
    }

    private fun findOpposingPiece(state: RoyalLudoState, absSquare: Int, myColour: Int): Int? {
        for ((seat, colour) in state.seatColours) {
            if (colour == myColour || seat in state.removalOrder) continue
            val startSq = RoyalLudoBoardUtils.startSquareFor(colour)
            for ((idx, progress) in state.piecesForSeat(seat).withIndex())
                if (progress in 0..PROGRESS_ENTER_HOME && RoyalLudoBoardUtils.absoluteSquare(startSq, progress) == absSquare) return seat
        }
        return null
    }

    private fun countPiecesOnSquare(state: RoyalLudoState, absSquare: Int, colour: Int): Int {
        val startSq = RoyalLudoBoardUtils.startSquareFor(colour)
        return state.seatColours.filter { it.value == colour }.keys.sumOf { seat ->
            state.piecesForSeat(seat).count { it in 0..PROGRESS_ENTER_HOME && RoyalLudoBoardUtils.absoluteSquare(startSq, it) == absSquare }
        }
    }

    private fun findPieceOnSquare(state: RoyalLudoState, absSquare: Int, seat: Int): Int {
        val startSq = RoyalLudoBoardUtils.startSquareFor(state.colourForSeat(seat))
        return state.piecesForSeat(seat).withIndex().first { it.value in 0..PROGRESS_ENTER_HOME && RoyalLudoBoardUtils.absoluteSquare(startSq, it.value) == absSquare }.index
    }

    private fun advanceTurn(state: RoyalLudoState, events: MutableList<GameEvent>): RoyalLudoState {
        val nextSeat = nextActiveSeat(state, state.finishingOrder)
        return state.copy(currentSeat = nextSeat, phase = Phase.AWAITING_ROLL, legalPieces = emptyList(), lastRoll = null, turnCounter = state.turnCounter + 1, turn = RoyalLudoTurnState(SeatNumber(nextSeat), Phase.AWAITING_ROLL.name), turnStartPieces = state.pieces, consecutiveSixes = 0)
    }

    private fun nextActiveSeat(state: RoyalLudoState, finishingOrder: List<Int>): Int {
        val activeSeats = state.seats.map { it.value }.filter { it !in finishingOrder && it !in state.removalOrder }
        if (activeSeats.isEmpty()) return state.currentSeat
        val currentIndex = activeSeats.indexOf(state.currentSeat)
        return if (currentIndex >= 0) activeSeats[(currentIndex + 1) % activeSeats.size] else activeSeats.first()
    }
}

@Serializable
data class RoyalLudoSeatView(val seat: Int, val colour: Int, val pieces: List<Int>, val status: String, val finished: Boolean)
@Serializable
data class RoyalLudoView(val seats: List<RoyalLudoSeatView>, val currentSeat: Int, val phase: String, val lastRoll: Int?, val legalMoves: List<Int>, val finishingOrder: List<Int>, val turnCounter: Long) : GameView

/** The roll that brings a piece out of base. */
private const val SIX = 6
/** Chances in hundredths of a percent: a fair six is 1667 in 10000. */
private const val BASIS = 10_000
private const val FAIR_SIX_BASIS = 1_667
private const val PCT_TO_BASIS = 100
