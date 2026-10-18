package com.kglabs28.yapnplay.backend.application.match.games.citybazaar

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
) : GameSession {
    private val startedAt = clock.nowMillis()
    private var auctionEndsAt = 0L
    private var lastBidAt = 0L
    private val mapper = CityBazaarWireMapper(json)

    override val currentSeat: Int get() = state.currentSeat
    override val phase: String get() = state.phase.name
    override val isFinished: Boolean get() = state.phase == CityBazaarPhase.FINISHED
    override val ranking: List<Int> get() = if (isFinished) CityBazaarEngine.ranking(state) else emptyList()
    override val turnSeconds: Int get() = engine.timing.turnSeconds.toInt()
    override val thinkMillis: Long = THINK_MS
    override val waitsForTick: Boolean get() = state.phase == CityBazaarPhase.AUCTION

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
            BotMode.FULL -> engine.autoplay(state, SeatNumber(seat), random)
            BotMode.TIMEOUT -> engine.cautiousPlay(state, SeatNumber(seat), random, lateFine = true)
        },
    )

    override fun tick(now: Long, cpuSeats: Set<Int>): List<GameEvent>? {
        if (isFinished) return null
        state.cbRules.house.timeLimitMinutes?.let { limit ->
            if (now - startedAt >= limit * MILLIS_PER_MINUTE) return apply(state.currentSeat, EndMatch)
        }
        val auction = state.auction ?: return null
        if (now >= auctionEndsAt) return apply(state.currentSeat, CloseAuction)
        if (now - lastBidAt < CPU_BID_DELAY_MS) return null
        val bidder = cpuSeats.filter { it != auction.seller && engine.bot.wantsToBid(state, it) }
            .shuffled()
            .firstOrNull { random.nextInt(PERCENT) < CPU_BID_CHANCE_PERCENT }
            ?: return null
        return apply(bidder, PlaceBid)
    }

    override fun pauseAfter(events: List<GameEvent>): Long = events.sumOf { event ->
        when (event) {
            is DiceRolled -> AFTER_ROLL_MS
            is TokenMoved -> if (event.jump) AFTER_MOVE_MS else PER_STEP_MS * abs(event.steps) + AFTER_MOVE_MS
            is CardDrawn -> CARD_MS
            is MoneyMoved -> MONEY_MS
            else -> 0L
        }
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
        ),
    ).jsonObject

    override fun legalMoves(viewerSeat: Int?): List<LegalMove> {
        val rolling = state.phase == CityBazaarPhase.ROLL || state.phase == CityBazaarPhase.JAIL_ROLL
        return if (viewerSeat == state.currentSeat && rolling) listOf(LegalMove.RollDice) else emptyList()
    }

    override fun wireEvents(events: List<GameEvent>): List<JsonObject> = mapper.toWire(events)

    private fun take(result: ApplyResult): List<GameEvent>? {
        if (result !is ApplyResult.Success) return null
        state = result.state as CityBazaarState
        if (result.events.any { it is AuctionStarted || it is BidPlaced }) {
            val now = clock.nowMillis()
            auctionEndsAt = now + AUCTION_SECONDS * BackendConstants.MILLIS_PER_SECOND
            lastBidAt = now
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
        const val THINK_MS = 1_000L
        const val AFTER_ROLL_MS = 2_100L
        const val PER_STEP_MS = 200L
        const val AFTER_MOVE_MS = 500L
        const val CARD_MS = 1_800L
        const val MONEY_MS = 700L
        const val CPU_BID_DELAY_MS = 1_200L
        const val CPU_BID_CHANCE_PERCENT = 35
        const val PERCENT = 100
        const val MILLIS_PER_MINUTE = 60_000L
        const val ACTION = "action"
    }
}
