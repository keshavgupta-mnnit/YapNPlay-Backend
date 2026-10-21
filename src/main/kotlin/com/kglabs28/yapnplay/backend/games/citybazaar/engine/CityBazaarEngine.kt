package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ExtendMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ReachTimeLimit
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
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.base.play.GameView
import com.kglabs28.yapnplay.backend.games.base.play.TypedRejection
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules
import com.kglabs28.yapnplay.backend.games.base.rules.GameShowcase
import com.kglabs28.yapnplay.backend.games.base.rules.MatchmakingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TimingConfig
import com.kglabs28.yapnplay.backend.games.base.rules.TurnPolicy
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuildOnTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.CloseAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclareBankruptcy
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclineTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.EndMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.LateRollFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PlaceBid
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellBuilding
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SkipBuild
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StartAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_DISPLAY_NAME
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_FIXED_RULES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_HOUSE_RULES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_ID
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_MAX_PLAYERS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_MIN_PLAYERS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_QUICK_PLAY_SEATS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_SEARCH_SECONDS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_TAGLINES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CITY_BAZAAR_TURN_SECONDS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.START_TILE

/**
 * City Bazaar: the Indian city-trading board game (docs/city-bazaar-rules.md).
 * Pure: dice come from the injected [GameRandom]. Bids come from any player;
 * the server applies [CloseAuction], [EndMatch] and [LateRollFine].
 */
class CityBazaarEngine : Game {
    override val id = GameId(CITY_BAZAAR_ID)
    override val displayName = CITY_BAZAAR_DISPLAY_NAME
    override val timing = TimingConfig(turnSeconds = CITY_BAZAAR_TURN_SECONDS)
    override val matchmaking = MatchmakingConfig(seats = CITY_BAZAAR_QUICK_PLAY_SEATS, searchSeconds = CITY_BAZAAR_SEARCH_SECONDS)
    override val showcase = GameShowcase(taglines = CITY_BAZAAR_TAGLINES, artKey = CITY_BAZAAR_ID)
    override val houseRuleOptions = CITY_BAZAAR_HOUSE_RULES
    override val fixedRules = CITY_BAZAAR_FIXED_RULES
    override val minPlayers = CITY_BAZAAR_MIN_PLAYERS
    override val maxPlayers = CITY_BAZAAR_MAX_PLAYERS
    override val maxSpectators: Int? = null
    override val turnPolicy: TurnPolicy = TurnPolicy.Sequential
    /** CPU seats: buys, builds and bids. */
    /** CPU seats, away and bot-held seats. */
    override val bot = CityBazaarBot(CityBazaarBotProfile.CPU)
    /** Timed-out turns: rolls and moves, sells only to pay. */
    val cautiousBot = CityBazaarBot(CityBazaarBotProfile.CAUTIOUS)

    override fun defaultRules(): GameRules = CityBazaarRules.STANDARD

    override fun rulesSummary(): String =
        "City Bazaar for 3–6 players (random tables 4): buy cities, build on your own, " +
            "double rent with 3 of a colour, ₹1,500 at START; sell or auction to pay debts; " +
            "last one solvent wins, or the richest when time or rounds run out."

    override fun start(rules: GameRules, seatCount: Int, random: GameRandom): GameState {
        require(seatCount in 2..CITY_BAZAAR_MAX_PLAYERS) { "City Bazaar needs 2-6 seats" }
        val cbRules = rules as? CityBazaarRules ?: CityBazaarRules.STANDARD
        val seats = (1..seatCount).toList()
        return CityBazaarState(
            gameId = id,
            cbRules = cbRules,
            seats = seats.map { SeatNumber(it) },
            seatCount = seatCount,
            positions = seats.associateWith { START_TILE },
            cash = seats.associateWith { cbRules.mandatory.startingCash },
            currentSeat = 1,
            phase = CityBazaarPhase.ROLL,
        )
    }

    override fun apply(state: GameState, seat: SeatNumber, action: Action, random: GameRandom): ApplyResult {
        val s = state as CityBazaarState
        if (s.phase == CityBazaarPhase.FINISHED) return reject("MATCH_ENDED", "Match ended")
        val me = seat.value
        val turn = CityBazaarTurn(s, random)
        // Any player may bid; everything else is the current seat's (or the server's on their behalf).
        if (action is PlaceBid) return if (s.phase == CityBazaarPhase.AUCTION && turn.bid(me)) turn.result() else reject("ILLEGAL_ACTION", "Can't bid now")
        // At the limit the match layer runs the vote; the engine only extends or ends.
        if (action is ExtendMatch) {
            return if (s.phase == CityBazaarPhase.EXTENSION_VOTE) turn.apply { extend(action.extraRounds, action.extraMinutes) }.result() else reject("ILLEGAL_ACTION", "Not at the limit")
        }
        if (action is ReachTimeLimit) return if (s.phase == CityBazaarPhase.ROLL) turn.apply { reachTimeLimit() }.result() else reject("ILLEGAL_ACTION", "Mid-turn")
        if (me != s.currentSeat) return reject("NOT_YOUR_TURN", "Not your turn")
        val ok = when (action) {
            is RollDice -> when (s.phase) {
                CityBazaarPhase.ROLL -> true.also { turn.roll() }
                CityBazaarPhase.JAIL_ROLL -> true.also { turn.jailRoll() }
                else -> false
            }
            BuyTile -> (s.phase == CityBazaarPhase.BUY && turn.buy(me)).also { if (it) turn.decided() }
            DeclineTile -> (s.phase == CityBazaarPhase.BUY).also { if (it) turn.decided() }
            is BuildOnTile -> (s.phase == CityBazaarPhase.BUILD && action.tile == s.pendingTile && turn.build(me, action.tile)).also { if (it) turn.decided() }
            SkipBuild -> (s.phase == CityBazaarPhase.BUILD).also { if (it) turn.decided() }
            PayJailFine -> s.phase in JAIL_DECISION && turn.payJailFine(me)
            StayInJail -> (s.phase in JAIL_DECISION).also { if (it) turn.stayInJail(me) }
            is SellBuilding -> (s.phase == CityBazaarPhase.DEBT && turn.sellBuilding(me, action.tile)).also { if (it) turn.settleDebtIfPossible() }
            // House rule "debt_auction" (on by default): a city sold to pay a debt goes up for auction first.
            is SellTile -> if (s.cbRules.house.debtAuction) {
                s.phase == CityBazaarPhase.DEBT && turn.startAuction(me, action.tile)
            } else {
                (s.phase == CityBazaarPhase.DEBT && turn.sellTile(me, action.tile)).also { if (it) turn.settleDebtIfPossible() }
            }
            is StartAuction -> s.phase == CityBazaarPhase.DEBT && turn.startAuction(me, action.tile)
            CloseAuction -> s.phase == CityBazaarPhase.AUCTION && turn.closeAuction()
            DeclareBankruptcy -> (s.phase == CityBazaarPhase.DEBT).also { if (it) turn.bankrupt(me, s.debt?.payees.orEmpty()) }
            LateRollFine -> (s.phase == CityBazaarPhase.ROLL || s.phase == CityBazaarPhase.JAIL_ROLL).also { if (it) turn.lateRollFine(me) }
            EndMatch -> true.also { turn.finish() }
            else -> return reject("BAD_REQUEST", "Unknown action for City Bazaar")
        }
        return if (ok) turn.result() else reject("ILLEGAL_ACTION", "Not allowed now")
    }

    override fun currentActor(state: GameState) = SeatNumber((state as CityBazaarState).currentSeat)

    /** A CPU seat's move. */
    override fun autoplay(state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult = playWith(bot, state, seat, random)

    /** Plays one step of [seat]'s turn with [player] (e.g. a player's Autoplay profile). */
    fun playAs(player: CityBazaarBot, state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult = playWith(player, state, seat, random)

    /**
     * Time ran out: roll and move, decline buying and
     * building, stay in jail on half rent, sell only what a debt needs.
     * [lateFine]: the user didn't roll and Autoplay is off (₹50 first).
     */
    fun cautiousPlay(state: GameState, seat: SeatNumber, random: GameRandom, lateFine: Boolean): ApplyResult {
        val s = state as CityBazaarState
        if (!lateFine || (s.phase != CityBazaarPhase.ROLL && s.phase != CityBazaarPhase.JAIL_ROLL)) return playWith(cautiousBot, s, seat, random)
        val fined = apply(s, seat, LateRollFine, random) as? ApplyResult.Success ?: return playWith(cautiousBot, s, seat, random)
        return when (val then = playWith(cautiousBot, fined.state, seat, random)) {
            is ApplyResult.Success -> ApplyResult.Success(then.state, fined.events + then.events)
            is ApplyResult.Rejected -> fined
        }
    }

    private fun playWith(player: CityBazaarBot, state: GameState, seat: SeatNumber, random: GameRandom): ApplyResult {
        val action = player.chooseAction(state, seat) ?: return reject("NO_ACTION", "Nothing to do")
        return apply(state, seat, action, random)
    }

    override fun removeSeat(state: GameState, seat: SeatNumber): GameState {
        val s = state as CityBazaarState
        if (seat.value !in s.active) return s
        val turn = CityBazaarTurn(s, NO_RANDOM)
        turn.remove(seat.value)
        return turn.state()
    }

    override fun viewFor(state: GameState, viewer: Viewer): GameView = CityBazaarView(state as CityBazaarState)

    override fun isFinished(state: GameState): Boolean = (state as CityBazaarState).phase == CityBazaarPhase.FINISHED

    override fun outcome(state: GameState): MatchOutcome {
        val s = state as CityBazaarState
        return MatchOutcome(
            ranking(s).mapIndexed { index, seat ->
                RankedSeat(SeatNumber(seat), if (seat in s.active) SeatStatus.FINISHED else SeatStatus.REMOVED, index + 1)
            },
        )
    }

    private fun reject(code: String, message: String) = ApplyResult.Rejected(TypedRejection(code, message))

    companion object {
        /** Players still in by net worth, then bankrupt players (latest first). */
        fun ranking(s: CityBazaarState): List<Int> =
            s.active.sortedByDescending { CityBazaarEconomy.netWorth(s, it) } + s.bankruptOrder.reversed()

        /** Removing a seat never rolls. */
        private val NO_RANDOM = object : GameRandom {
            override fun nextInt(bound: Int): Int = 0
        }
    }
}

/** Just jailed: roll for doubles, or pay the fine / take half rent straight away. */
private val JAIL_DECISION = setOf(CityBazaarPhase.JAIL_ROLL, CityBazaarPhase.JAIL_CHOICE)
