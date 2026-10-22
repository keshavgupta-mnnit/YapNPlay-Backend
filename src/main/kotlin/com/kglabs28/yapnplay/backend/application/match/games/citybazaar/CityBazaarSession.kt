package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

import com.kglabs28.yapnplay.backend.application.match.games.BotThinkTime
import com.kglabs28.yapnplay.backend.games.base.play.BotTraceEntry
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBotProfile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarBot
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.SentToJail
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.LeftJail
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.RestTaken
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.WentBankrupt
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BuildingBuilt
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CantAfford
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.AuctionEnded
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TurnSkipped
import com.kglabs28.yapnplay.backend.application.match.actor.ExtensionDecided
import com.kglabs28.yapnplay.backend.application.match.games.ExtensionOffer
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarLimit
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ReachTimeLimit
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.ExtendMatch
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarBank
import com.kglabs28.yapnplay.backend.application.match.games.BotMode
import com.kglabs28.yapnplay.backend.application.match.games.GameSession
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.Colour
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarAuction
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarDebt
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarPublicState
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarSeatState
import com.kglabs28.yapnplay.backend.contract.citybazaar.CityBazaarWireAction
import com.kglabs28.yapnplay.backend.contract.transport.MessageType
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.AuctionStarted
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.BidPlaced
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CardDrawn
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEconomy
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarEngine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarPhase
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.CityBazaarState
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.DiceRolled
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.MoneyMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.TokenMoved
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuildOnTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.CloseAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclareBankruptcy
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclineTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.EndMatch
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PlaceBid
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellBuilding
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SkipBuild
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StartAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.AUCTION_SECONDS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BidSteps
import com.kglabs28.yapnplay.backend.games.elements.board.protocol.LegalMove
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.math.abs

/**
 * City Bazaar inside a match: the engine, the auction clock (5 s after
 * each bid, CPU seats bidding), the match time limit, and the wire shapes.
 */
class CityBazaarSession(
    private val engine: CityBazaarEngine,
    private var state: CityBazaarState,
    private val random: GameRandom,
    private val json: Json,
    private val clock: Clock,
    private val startedAt: Long = clock.nowMillis(),
    private var auctionEndsAt: Long = 0L,
    private var lastBidAt: Long = 0L,
    private val timing: CityBazaarTiming = CityBazaarTiming(),
    /** The bots' recent decisions, for the admin page. */
    private val trace: BotTrace = BotTrace(),
    /** How a player's Autoplay plays (reserves are runtime settings). */
    private val autoplayBot: CityBazaarBot = CityBazaarBot(CityBazaarBotProfile.AUTOPLAY),
    /** How CPU, away and bot-held seats play (tuning from runtime settings). */
    private val cpuBot: CityBazaarBot = CityBazaarBot(CityBazaarBotProfile.CPU),
) : GameSession {
    /** How long CPUs wait after the last bid before bidding again. */
    private var bidGap: Long = CPU_BID_DELAY_MS
    private val mapper = CityBazaarWireMapper(json)

    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == CityBazaarPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) CityBazaarEngine.ranking(state) else emptyList()
    override val turnSeconds: Int get() = timing.turnSeconds
    /** A bot's think time: longer for buying, building, jail and debt. */
    override val thinkMillis: Long get() = timing.think.millis(state.phase in THINK_DECISIONS)
    // An auction or the keep-playing vote: no turn timer, the tick closes them.
    override val waitsForTick: Boolean get() = state.phase == CityBazaarPhase.AUCTION || state.phase == CityBazaarPhase.EXTENSION_VOTE

    override fun colourOf(seat: Int): Colour = Colour.entries[(seat - 1) % Colour.entries.size]

    override fun decode(type: String, payload: JsonObject): Action? = when (type) {
        MessageType.ROLL_DICE -> RollDice
        MessageType.GAME_ACTION -> payload[ACTION]?.jsonObject
            ?.let { runCatching { json.decodeFromJsonElement(CityBazaarWireAction.serializer(), it) }.getOrNull() }
            ?.let(::toEngine)
        else -> null
    }

    override fun apply(seat: Int, action: Action): List<GameEvent>? =
        take(engine.apply(state, SeatNumber(seat), action, random))

    override fun botPlay(seat: Int, mode: BotMode): List<GameEvent>? = take(
        when (mode) {
            BotMode.FULL -> engine.playAs(cpuBot, state, SeatNumber(seat), random)
            BotMode.AUTOPLAY -> engine.playAs(autoplayBot, state, SeatNumber(seat), random)
            BotMode.TIMEOUT -> engine.cautiousPlay(state, SeatNumber(seat), random, lateFine = true)
        },
    )

    override fun tick(now: Long, cpuSeats: Set<Int>, botSeats: Set<Int>): List<GameEvent>? {
        if (isFinished) return null
        state.cbRules.house.timeLimitMinutes?.let { limit ->
            // Time is up: between turns the match pauses for the keep-playing vote.
            if (now - startedAt >= limit * MILLIS_PER_MINUTE && state.phase == CityBazaarPhase.ROLL) {
                return apply(state.currentSeat, ReachTimeLimit)
            }
        }

        val auction = state.auction ?: return null
        if (now >= auctionEndsAt) return apply(state.currentSeat, CloseAuction)
        // CPUs bid after a human-like, random pause (re-drawn after every bid).
        if (now - lastBidAt < bidGap) return null
        // Seats the server plays: a player's Autoplay bids with its own profile, CPUs and held seats with the CPU's.
        val bidder = cpuSeats.filter { seat ->
            val bot = if (seat in botSeats) cpuBot else autoplayBot
            seat != auction.seller && bot.wantsToBid(state, seat)
        }
            .shuffled()
            .firstOrNull { random.nextInt(PERCENT) < CPU_BID_CHANCE_PERCENT }
            ?: return null
        return apply(bidder, PlaceBid)
    }

    override val atLimit: Boolean get() = state.phase == CityBazaarPhase.EXTENSION_VOTE

    override val stillIn: Set<Int> get() = state.active.toSet()

    override val extension: ExtensionOffer
        get() = when (state.limitHit) {
            CityBazaarLimit.ROUNDS -> ExtensionOffer(extraRounds = state.cbRules.house.extensionRounds)
            CityBazaarLimit.TIME -> ExtensionOffer(extraMinutes = state.cbRules.house.extensionMinutes)
            null -> ExtensionOffer()
        }

    override fun extend(): List<GameEvent>? =
        apply(state.currentSeat, ExtendMatch(state.cbRules.house.extensionRounds, state.cbRules.house.extensionMinutes))

    override fun endAtLimit(): List<GameEvent>? = apply(state.currentSeat, EndMatch)

    // Not during an auction or the keep-playing vote: those settle first.
    override val botTrace: List<BotTraceEntry> get() = trace.recent()

    override val canEndEarly: Boolean
        get() = !isFinished && state.phase != CityBazaarPhase.AUCTION && state.phase != CityBazaarPhase.EXTENSION_VOTE

    /** Ends like the time limit: ranked by net worth. */
    override fun endEarly(): List<GameEvent>? = apply(state.currentSeat, EndMatch)

    /**
     * Dice and hop animations, then ~2.5 s for each event the table shows as
     * a message (capped at 8 s), so the next turn's timer starts only after
     * players have read them.
     */
    override fun pauseAfter(events: List<GameEvent>): Long {
        val animation = events.sumOf { event ->
            when (event) {
                is DiceRolled -> AFTER_ROLL_MS
                is TokenMoved -> if (event.jump) AFTER_MOVE_MS else PER_STEP_MS * abs(event.steps) + AFTER_MOVE_MS
                else -> 0L
            }
        }
        val messages = events.count { it.showsMessage() }
        return animation + (timing.messageMillis * messages).coerceAtMost(timing.maxMessageMillis)
    }

    private fun GameEvent.showsMessage(): Boolean = when (this) {
        is MoneyMoved, is CardDrawn, is SentToJail, is LeftJail, is RestTaken, is WentBankrupt, is BuildingBuilt,
        is CantAfford, is AuctionStarted, is AuctionEnded, is TurnSkipped, is ExtensionDecided -> true
        else -> false
    }

    override fun removeSeat(seat: Int) {
        state = engine.removeSeat(state, SeatNumber(seat)) as CityBazaarState
    }

    override fun publicState(viewerSeat: Int?): JsonObject = json.encodeToJsonElement(
        CityBazaarPublicState.serializer(),
        CityBazaarPublicState(
            players = (1..state.seatCount).map { seat ->
                CityBazaarSeatState(
                    seat = seat,
                    cash = state.cashOf(seat),
                    position = state.positions[seat] ?: 0,
                    inJail = state.inJail(seat),
                    halfRent = seat in state.halfRent,
                    bankrupt = seat !in state.active,
                    rentCollected = state.rentBySeat[seat] ?: 0,
                    rentPaid = state.rentPaidBySeat[seat] ?: 0,
                )
            },
            owners = state.owners,
            levels = state.levels,
            phase = state.phase.name,
            die1 = state.die1,
            die2 = state.die2,
            pendingTile = state.pendingTile,
            debt = state.debt?.let { CityBazaarDebt(it.total, it.reason.name, it.tile) },
            jailTriesLeft = state.jailTriesLeft,
            auction = state.auction?.let {
                CityBazaarAuction(it.tile, it.seller, it.bid, it.leader, if (it.leader == null) it.bid else it.bid + BidSteps.after(it.bid), auctionEndsAt)
            },
            timeEndsAt = state.cbRules.house.timeLimitMinutes?.let { startedAt + it * MILLIS_PER_MINUTE },
            round = state.round,
            roundLimit = state.cbRules.house.roundLimit,
            netWorth = (1..state.seatCount).associateWith { CityBazaarEconomy.netWorth(state, it) },
            ranking = ranking,
            rentCollected = state.rentByTile,
            debtAuction = state.cbRules.house.debtAuction,
            moneyPopups = state.cbRules.house.moneyPopups,
            bank = state.cbRules.house.bankStart.toLong().let { start ->
                CityBazaarBank(cash = start + state.bankReceived - state.bankPaid, received = state.bankReceived, paid = state.bankPaid)
            },
        ),
    ).jsonObject

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> {
        val rolling = state.phase == CityBazaarPhase.ROLL || state.phase == CityBazaarPhase.JAIL_ROLL
        return if (viewerSeat == state.currentSeat && rolling) listOf(LegalMove.RollDice) else emptyList()
    }

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = mapper.toWire(events)

    override fun save(): JsonObject = json.encodeToJsonElement(
        CityBazaarSaved.serializer(),
        CityBazaarSaved(state, startedAt, auctionEndsAt, lastBidAt),
    ).jsonObject

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        state = result.state as CityBazaarState
        if (result.events.any { it is AuctionStarted || it is BidPlaced }) {
            val now = clock.nowMillis()
            auctionEndsAt = now + AUCTION_SECONDS * BackendConstants.MILLIS_PER_SECOND
            lastBidAt = now
            bidGap = timing.think.millis(decision = false).coerceAtLeast(CPU_BID_DELAY_MS)
        }
        return result.events
    }

    private fun toEngine(action: CityBazaarWireAction): Action = when (action) {
        CityBazaarWireAction.Roll -> RollDice
        CityBazaarWireAction.Buy -> BuyTile
        CityBazaarWireAction.Decline -> DeclineTile
        is CityBazaarWireAction.Build -> BuildOnTile(action.tile)
        CityBazaarWireAction.SkipBuild -> SkipBuild
        is CityBazaarWireAction.SellBuilding -> SellBuilding(action.tile)
        is CityBazaarWireAction.SellTile -> SellTile(action.tile)
        is CityBazaarWireAction.StartAuction -> StartAuction(action.tile)
        CityBazaarWireAction.Bid -> PlaceBid
        CityBazaarWireAction.PayJailFine -> PayJailFine
        CityBazaarWireAction.StayInJail -> StayInJail
        CityBazaarWireAction.DeclareBankruptcy -> DeclareBankruptcy
    }

    private companion object {
        const val AFTER_ROLL_MS = 2_100L
        const val PER_STEP_MS = 200L
        const val AFTER_MOVE_MS = 500L
        const val CPU_BID_DELAY_MS = 1_200L
        const val CPU_BID_CHANCE_PERCENT = 35
        const val PERCENT = 100
        const val MILLIS_PER_MINUTE = 60_000L
        const val ACTION = "action"
    }
}

/** A saved City Bazaar session: the engine state plus the clocks the session keeps. */
@Serializable
data class CityBazaarSaved(
    val engine: CityBazaarState,
    val startedAt: Long,
    val auctionEndsAt: Long,
    val lastBidAt: Long,
)

/** Turn time, bot think time and reading time per table message (runtime settings). */
data class CityBazaarTiming(
    val turnSeconds: Int = GameSettings.CB_TURN_SECONDS.default,
    val think: BotThinkTime = BotThinkTime(
        GameSettings.CB_CPU_THINK_MS.default.toLong(),
        GameSettings.CB_CPU_THINK_JITTER_MS.default.toLong(),
        GameSettings.CB_CPU_DECISION_EXTRA_MS.default.toLong(),
    ),
    val messageMillis: Long = GameSettings.CB_MESSAGE_PAUSE_MS.default.toLong(),
    val maxMessageMillis: Long = GameSettings.CB_MESSAGE_PAUSE_CAP_MS.default.toLong(),
)

/** City Royale phases where a bot weighs a real choice (it thinks a little longer). */
private val THINK_DECISIONS = setOf(CityBazaarPhase.BUY, CityBazaarPhase.BUILD, CityBazaarPhase.JAIL_CHOICE, CityBazaarPhase.DEBT)
