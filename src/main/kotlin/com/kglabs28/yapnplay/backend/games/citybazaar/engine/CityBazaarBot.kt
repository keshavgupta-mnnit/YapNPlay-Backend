package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.BotDecision
import com.kglabs28.yapnplay.backend.games.base.play.BotTrace
import com.kglabs28.yapnplay.backend.games.base.play.Evaluation
import com.kglabs28.yapnplay.backend.games.base.play.GameBot
import com.kglabs28.yapnplay.backend.games.base.play.Standing
import com.kglabs28.yapnplay.backend.games.elements.dice.mechanics.RollDice
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuildOnTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.BuyTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclareBankruptcy
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.DeclineTile
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.PayJailFine
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SellBuilding
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.SkipBuild
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StartAuction
import com.kglabs28.yapnplay.backend.games.citybazaar.engine.action.StayInJail
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BidSteps
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_FINE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TILE_COUNT
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TileKind
import kotlin.math.pow

/**
 * City Bazaar's computer player. Every choice is a [BotDecision]: each
 * option gets an expected gain (rent it should still bring in, rent kept
 * from rivals, pressure on a rival close to going broke), a cost now, and a
 * risk (the chance of running out of cash in the next two turns × the net
 * worth at stake); the best gain − cost − risk × weight is played. The
 * weight grows with the seat's standing (behind → bolder, leading →
 * careful). Debts are paid with [CityBazaarDebtPlanner]; a bare city always
 * goes to auction first (never worth less than the bank's price). A profile
 * that doesn't play only rolls and moves, stays in jail on half rent, and
 * never buys, builds or bids. Decisions go to [trace] for the admin page.
 */
class CityBazaarBot(val profile: CityBazaarBotProfile, private val trace: BotTrace? = null) : GameBot {
    private val cautious = !profile.plays
    private val tuning = profile.tuning

    override fun chooseAction(state: GameState, seat: SeatNumber): Action? {
        val s = state as CityBazaarState
        val me = seat.value
        if (s.currentSeat != me) return null
        return when (s.phase) {
            // Bots never vote on keeping the match going.
            CityBazaarPhase.FINISHED, CityBazaarPhase.AUCTION, CityBazaarPhase.EXTENSION_VOTE -> null
            CityBazaarPhase.ROLL, CityBazaarPhase.JAIL_ROLL -> RollDice
            CityBazaarPhase.JAIL_CHOICE -> if (cautious) StayInJail else JailChoice(s, me).choose(trace)
            CityBazaarPhase.DEBT -> {
                val debt = s.debt?.total ?: 0
                if (s.cashOf(me) + CityBazaarEconomy.liquidationValue(s, me) < debt) {
                    DeclareBankruptcy
                } else {
                    CityBazaarDebtPlanner.next(s, me, tuning)?.let { (tile, building) -> if (building) SellBuilding(tile) else StartAuction(tile) }
                        ?: DeclareBankruptcy
                }
            }
            CityBazaarPhase.BUY -> {
                val tile = s.pendingTile ?: return DeclineTile
                if (cautious) DeclineTile else BuyChoice(s, me, tile, CityBazaarBoard.tile(tile).price).choose(trace)
            }
            CityBazaarPhase.BUILD -> {
                val tile = s.pendingTile ?: return SkipBuild
                if (cautious) SkipBuild else BuildChoice(s, me, tile).choose(trace)
            }
        }
    }

    /** Whether [seat] (played by this bot) bids the next step: when winning at that price is worth more than passing. */
    fun wantsToBid(s: CityBazaarState, seat: Int): Boolean {
        if (cautious) return false
        val a = s.auction ?: return false
        if (seat == a.seller || seat == a.leader || seat !in s.active) return false
        val next = if (a.leader == null) a.bid else a.bid + BidSteps.after(a.bid)
        if (s.cashOf(seat) < next) return false
        return BuyChoice(s, seat, a.tile, next).choose(trace) == BuyTile
    }

    /** The risk weight for [seat]: the ruin weight × its standing (net worth vs the richest). */
    private fun riskWeight(s: CityBazaarState, seat: Int): Double {
        val best = s.active.maxOf { CityBazaarEconomy.netWorth(s, it) }.toDouble()
        val standing = Standing.factor(CityBazaarEconomy.netWorth(s, seat).toDouble(), best, tuning.standingMinPct / PERCENT, tuning.standingMaxPct / PERCENT)
        return tuning.ruinWeightPct / PERCENT * standing
    }

    /** Chance of running short (below the profile's reserve) in the next two turns with [cash] × the net worth at stake. */
    private fun ruinRisk(s: CityBazaarState, seat: Int, cash: Int): Double =
        CityBazaarOutlook.ruinChance(s, seat, cash - profile.reserve) * CityBazaarEconomy.netWorth(s, seat).coerceAtLeast(0)

    /**
     * Buy (or win an auction) at [cost], or pass. Worth: the rent it should
     * bring in, or what keeping it from a rival is worth, whichever is more.
     * A city counts at its full price in net worth, so the real cost is
     * [cost] − price (paying less is profit: bidding low on a rival's debt
     * auction).
     */
    inner class BuyChoice(private val s: CityBazaarState, private val seat: Int, private val tile: Int, private val cost: Int) : BotDecision<Action> {
        override val name = "buy ${CityBazaarBoard.tile(tile).name} at $cost"
        override val riskWeight = riskWeight(s, seat)
        override fun options(): List<Action> = listOf(DeclineTile, BuyTile)
        override fun label(option: Action) = if (option == BuyTile) "buy" else "pass"

        override fun evaluate(option: Action): Evaluation = when (option) {
            BuyTile -> {
                val after = s.copy(owners = s.owners + (tile to seat), cash = s.cash + (seat to s.cashOf(seat) - cost))
                val own = gain(s, seat, tile)
                val denial = denial(s, seat, tile)
                Evaluation(
                    gain = maxOf(own, denial),
                    cost = (cost - CityBazaarBoard.tile(tile).price).toDouble(),
                    risk = ruinRisk(after, seat, s.cashOf(seat) - cost),
                    note = "rent ${own.toInt()}, denial ${denial.toInt()}",
                )
            }
            else -> Evaluation(risk = ruinRisk(s, seat, s.cashOf(seat)))
        }
    }

    /**
     * Build one more on the landed city, or skip (even building: never above
     * the least built city of the colour). Worth: the extra rent, more if
     * rivals are likely to land there soon, plus the chance of pushing a rival
     * who can't pay into debt. The risk sees the wealth tax rising.
     */
    inner class BuildChoice(private val s: CityBazaarState, private val seat: Int, private val tile: Int) : BotDecision<Action> {
        override val name = "build on ${CityBazaarBoard.tile(tile).name}"
        override val riskWeight = riskWeight(s, seat)
        override fun label(option: Action) = if (option is BuildOnTile) "build" else "skip"

        override fun options(): List<Action> {
            val group = CityBazaarBoard.tile(tile).group ?: return listOf(SkipBuild)
            val mine = CityBazaarBoard.groupTiles(group).filter { s.owners[it] == seat }
            return if (s.level(tile) > mine.minOf { s.level(it) }) listOf(SkipBuild) else listOf(SkipBuild, BuildOnTile(tile))
        }

        override fun evaluate(option: Action): Evaluation = when (option) {
            is BuildOnTile -> {
                val buildCost = CityBazaarBoard.tile(tile).buildCost
                val after = s.copy(levels = s.levels + (tile to s.level(tile) + 1), cash = s.cash + (seat to s.cashOf(seat) - buildCost))
                val newRent = CityBazaarEconomy.rent(after, tile)
                val rise = newRent - CityBazaarEconomy.rent(s, tile)
                val rivals = s.active.filter { it != seat }
                val soon = rivals.sumOf { rise * CityBazaarOutlook.landsWithinTwo(s.positions[it] ?: 0, tile) }
                val pressure = rivals.filter { s.cashOf(it) < newRent }
                    .sumOf { CityBazaarOutlook.landsWithinTwo(s.positions[it] ?: 0, tile) * tuning.pressureValue }
                Evaluation(
                    gain = future(after, seat) - future(s, seat) + soon + pressure,
                    risk = ruinRisk(after, seat, s.cashOf(seat) - buildCost),
                    note = "rent +$rise, soon ${soon.toInt()}, pressure ${pressure.toInt()}",
                )
            }
            else -> Evaluation(risk = ruinRisk(s, seat, s.cashOf(seat)))
        }
    }

    /**
     * Pay the fine now, or stay and try for doubles, as a person decides:
     * short of cash (the fine would leave less than the cash to keep): stay.
     * Plenty of cash and half rent costing at least the fine over the turns
     * in jail: pay. Otherwise compare: staying costs half the rent and the
     * buys missed; it saves the fine and only [CityBazaarBotTuning.jailPostponePct] %
     * of the rent moving would likely cost (the seat must move later anyway).
     * Turns in jail = expected tries until doubles (1 in 6), at most the tries left.
     */
    inner class JailChoice(private val s: CityBazaarState, private val seat: Int) : BotDecision<Action> {
        override val name = "jail"
        override fun options(): List<Action> = listOf(StayInJail, PayJailFine)
        override fun label(option: Action) = if (option == PayJailFine) "pay" else "stay"

        override fun evaluate(option: Action): Evaluation {
            if (option != PayJailFine) return Evaluation(note = "half rent")
            val tries = s.jailTriesLeft.coerceAtLeast(1)
            val turnsIn = (1 - (1 - DOUBLES).pow(tries)) / DOUBLES
            val rounds = CityBazaarOutlook.roundsLeft(s, tuning.defaultRounds)
            val halfRentPerTurn = future(s, seat) / rounds * HALF_RENT
            val keep = keepCash(s, seat)
            val cashAfter = s.cashOf(seat) - JAIL_FINE
            if (cashAfter < keep) return Evaluation.never("short of cash")
            if (cashAfter >= keep * tuning.comfortPct / PERCENT && halfRentPerTurn >= JAIL_FINE / turnsIn) return Evaluation.always("rich, half rent costs more")
            val pos = s.positions[seat] ?: 0
            val missed = (2..12).sumOf { d ->
                val tile = (pos + d) % TILE_COUNT
                val spec = CityBazaarBoard.tile(tile)
                if (!spec.buyable || s.owners[tile] != null || cashAfter < spec.price) {
                    0.0
                } else {
                    val buy = BuyChoice(s, seat, tile, spec.price)
                    CityBazaarOutlook.p(d) * (buy.evaluate(BuyTile).value(buy.riskWeight) - buy.evaluate(DeclineTile).value(buy.riskWeight)).coerceAtLeast(0.0)
                }
            } * turnsIn
            // Rent dodged by not moving is mostly only postponed.
            val rentAvoided = CityBazaarOutlook.expectedOwedNextRoll(s, seat).coerceAtLeast(0.0) * turnsIn * tuning.jailPostponePct / PERCENT
            return Evaluation(gain = halfRentPerTurn * turnsIn + missed, cost = JAIL_FINE + rentAvoided, note = "missed buys ${missed.toInt()}")
        }
    }

    private fun keepCash(s: CityBazaarState, seat: Int): Int =
        CityBazaarOutlook.keepCash(s, seat, profile.reserve, profile.safetyBuffer, tuning.ruinTargetPct)

    private fun future(s: CityBazaarState, seat: Int): Double = CityBazaarOutlook.future(s, seat, tuning.defaultRounds)

    /** Expected extra rent from owning [tile] (a completed set doubles all three, a pair doubles both companies). */
    private fun gain(s: CityBazaarState, seat: Int, tile: Int): Double =
        future(s.copy(owners = s.owners + (tile to seat)), seat) - future(s, seat)

    /** What keeping [tile] from the rival it helps most is worth (see [CityBazaarDebtPlanner.denial]). */
    private fun denial(s: CityBazaarState, seat: Int, tile: Int): Double = CityBazaarDebtPlanner.denial(s, seat, tile, tuning)

    companion object {
        private const val PERCENT = 100.0
        private const val DOUBLES = 1.0 / 6
        private const val HALF_RENT = 0.5

        /** What to sell next to pay the debt: (tile, true) for its top building, (tile, false) for the bare city. */
        fun nextSale(s: CityBazaarState, seat: Int, tuning: CityBazaarBotTuning = CityBazaarBotTuning()): Pair<Int, Boolean>? =
            CityBazaarDebtPlanner.next(s, seat, tuning)

        /** A rival owns another tile of [tile]'s colour, or its partner company: the tile would complete or strengthen their set. */
        internal fun strengthens(s: CityBazaarState, rival: Int, tile: Int): Boolean {
            val spec = CityBazaarBoard.tile(tile)
            if (spec.kind == TileKind.COMPANY) return spec.partner != null && s.owners[spec.partner] == rival
            val group = spec.group?.takeIf { it.isColourSet } ?: return false
            return CityBazaarBoard.groupTiles(group).any { it != tile && s.owners[it] == rival }
        }
    }
}
