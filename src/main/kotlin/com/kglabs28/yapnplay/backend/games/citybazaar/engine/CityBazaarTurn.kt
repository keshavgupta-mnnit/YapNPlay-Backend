package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.play.ApplyResult
import com.kglabs28.yapnplay.backend.games.base.play.GameEvent
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BidSteps
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CardDeck
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CardEffect
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarBoard
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.CityBazaarCards
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.DICE_FACES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_FINE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_TILE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.JAIL_TRIES
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.LATE_ROLL_FINE
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TILE_COUNT
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.TileKind

/**
 * One action's worth of play: works on a copy of the state and collects
 * the events. A move ends in a decision (BUY / BUILD / DEBT) or the next
 * player's turn. Doubles mean nothing, except as the way out of jail.
 */
internal class CityBazaarTurn(private var s: CityBazaarState, private val random: GameRandom) {
    private val events = mutableListOf<GameEvent>()
    /** The turn passed on during this action (nothing else may move it on). */
    private var ended = false

    fun result(): ApplyResult = ApplyResult.Success(withBankTotals(), events.toList())

    fun state(): CityBazaarState = withBankTotals()

    /** Adds this action's money to and from the bank to the match totals (once). */
    private var banked = false

    private fun withBankTotals(): CityBazaarState {
        if (!banked) {
            banked = true
            val money = events.filterIsInstance<MoneyMoved>()
            s = s.copy(
                bankReceived = s.bankReceived + money.filter { it.to == null }.sumOf { it.amount.toLong() },
                bankPaid = s.bankPaid + money.filter { it.from == null }.sumOf { it.amount.toLong() },
            )
        }
        return s
    }

    // ---------------- rolling and moving ----------------

    fun roll() {
        val seat = s.currentSeat
        val (d1, d2) = dice(seat)
        moveSteps(seat, d1 + d2)
        settle()
    }

    /** Just jailed: doubles free you (you move next turn); 3 misses and you choose fine or half rent. */
    fun jailRoll() {
        val seat = s.currentSeat
        val (d1, d2) = dice(seat)
        if (d1 == d2) {
            leaveJail(seat, "DOUBLES")
            return nextTurn()
        }
        val left = s.jailTriesLeft - 1
        s = s.copy(jailTriesLeft = left, phase = if (left > 0) CityBazaarPhase.JAIL_ROLL else CityBazaarPhase.JAIL_CHOICE)
    }

    private fun dice(seat: Int): Pair<Int, Int> {
        val d1 = random.nextInt(DICE_FACES) + 1
        val d2 = random.nextInt(DICE_FACES) + 1
        events += DiceRolled(seat, d1, d2)
        s = s.copy(die1 = d1, die2 = d2, turnCounter = s.turnCounter + 1)
        return d1 to d2
    }

    private fun moveSteps(seat: Int, steps: Int) {
        val from = s.positions[seat] ?: 0
        val raw = from + steps
        val to = raw % TILE_COUNT
        events += TokenMoved(seat, from, to, steps, jump = false)
        s = s.copy(positions = s.positions + (seat to to))
        // ₹1,500 every time START is passed or landed on.
        if (raw >= TILE_COUNT) receive(seat, s.cbRules.mandatory.startBonus, MoneyReason.START, null)
        land(seat, to)
    }

    private fun land(seat: Int, tile: Int) {
        val spec = CityBazaarBoard.tile(tile)
        when (spec.kind) {
            TileKind.START -> Unit
            TileKind.JAIL -> goToJail(seat)
            // Club: the lander treats everyone; Rest House: everyone pays the lander. One Money event per pair.
            TileKind.CLUB -> if (s.cbRules.house.clubFee) {
                val each = s.cbRules.house.clubRestEach
                val others = s.active.filter { it != seat }
                if (others.isNotEmpty()) charge(seat, each * others.size, others.associateWith { each }, MoneyReason.CLUB, tile)
            }
            TileKind.REST_HOUSE -> if (s.cbRules.house.restHouseSkips) {
                val each = s.cbRules.house.clubRestEach
                s.active.filter { it != seat }.forEach { payer ->
                    if (payer in s.active) charge(payer, each, mapOf(seat to each), MoneyReason.REST, tile)
                }
            }
            TileKind.TAX -> {
                val bill = spec.taxPerProperty * CityBazaarEconomy.properties(s, seat) + spec.taxPerBuilding * CityBazaarEconomy.buildings(s, seat)
                if (bill > 0) charge(seat, bill, emptyMap(), MoneyReason.TAX, tile)
            }
            TileKind.LUCK -> drawCard(seat, CardDeck.LUCK)
            TileKind.CHEST -> drawCard(seat, CardDeck.CHEST)
            TileKind.CITY, TileKind.COMPANY -> {
                val owner = s.owners[tile]
                when {
                    // Can't afford it: no offer, the move just goes on.
                    owner == null -> if (s.cashOf(seat) >= spec.price) {
                        s = s.copy(phase = CityBazaarPhase.BUY, pendingTile = tile)
                    } else {
                        events += CantAfford(seat, tile, spec.price)
                    }
                    // Your own city: offered one more house (or the hotel).
                    owner == seat -> if (CityBazaarEconomy.buildAllowed(s, seat, tile) && s.cashOf(seat) >= spec.buildCost) {
                        s = s.copy(phase = CityBazaarPhase.BUILD, pendingTile = tile)
                    }
                    else -> {
                        val rent = CityBazaarEconomy.rent(s, tile)
                        if (rent > 0) charge(seat, rent, mapOf(owner to rent), MoneyReason.RENT, tile)
                    }
                }
            }
        }
    }

    /** Luck / Shagun Chest: the dice total of the roll that brought the player here (2–12) picks the result. */
    private fun drawCard(seat: Int, deck: CardDeck) {
        val card = CityBazaarCards.forRoll(deck, s.die1 + s.die2)
        events += CardDrawn(seat, card.id)
        when (val e = card.effect) {
            is CardEffect.Money -> if (e.amount >= 0) {
                receive(seat, e.amount, MoneyReason.CARD, null)
            } else {
                charge(seat, -e.amount, emptyMap(), MoneyReason.CARD, null)
            }
            is CardEffect.AdvanceTo -> {
                val from = s.positions[seat] ?: 0
                val steps = (e.tile - from + TILE_COUNT) % TILE_COUNT
                if (steps == 0) land(seat, e.tile) else moveSteps(seat, steps)
            }
            CardEffect.GoToJail -> goToJail(seat)
            is CardEffect.MoveBack -> {
                val from = s.positions[seat] ?: 0
                val to = (from - e.steps + TILE_COUNT) % TILE_COUNT
                events += TokenMoved(seat, from, to, -e.steps, jump = false)
                s = s.copy(positions = s.positions + (seat to to))
                land(seat, to)
            }
            is CardEffect.CollectFromEach -> s.active.filter { it != seat }.forEach { other ->
                charge(other, e.amount, mapOf(seat to e.amount), MoneyReason.CARD, null)
            }
            is CardEffect.PayEach -> {
                val others = s.active.filter { it != seat }
                if (others.isNotEmpty()) charge(seat, e.amount * others.size, others.associateWith { e.amount }, MoneyReason.CARD, null)
            }
            is CardEffect.PerProperty -> {
                val bill = CityBazaarEconomy.properties(s, seat) * e.amount
                if (bill > 0) charge(seat, bill, emptyMap(), MoneyReason.CARD, null)
            }
        }
    }

    private fun goToJail(seat: Int) {
        val from = s.positions[seat] ?: 0
        if (from != JAIL_TILE) events += TokenMoved(seat, from, JAIL_TILE, 0, jump = true)
        events += SentToJail(seat)
        s = s.copy(positions = s.positions + (seat to JAIL_TILE), jailed = s.jailed + seat)
        // Decided right now, on this turn: roll for doubles, pay the fine or take half rent.
        if (seat == s.currentSeat) s = s.copy(phase = CityBazaarPhase.JAIL_ROLL, jailTriesLeft = JAIL_TRIES)
    }

    private fun leaveJail(seat: Int, how: String) {
        s = s.copy(jailed = s.jailed - seat)
        events += LeftJail(seat, how)
    }

    fun payJailFine(seat: Int): Boolean {
        if (s.cashOf(seat) < JAIL_FINE) return false
        pay(seat, JAIL_FINE, emptyMap(), MoneyReason.JAIL_FINE, JAIL_TILE)
        leaveJail(seat, "FINE")
        nextTurn()
        return true
    }

    /** Didn't pay: still in jail, rent collected is halved until the next turn. */
    fun stayInJail(seat: Int) {
        s = s.copy(halfRent = s.halfRent + seat)
        events += HalfRentTaken(seat)
        nextTurn()
    }

    fun lateRollFine(seat: Int) {
        val fine = minOf(s.cashOf(seat), LATE_ROLL_FINE)
        if (fine > 0) pay(seat, fine, emptyMap(), MoneyReason.LATE_ROLL, null)
    }

    // ---------------- money ----------------

    private fun receive(seat: Int, amount: Int, reason: MoneyReason, tile: Int?) {
        s = s.copy(cash = s.cash + (seat to s.cashOf(seat) + amount))
        events += MoneyMoved(null, seat, amount, reason, tile)
    }

    /**
     * [seat] pays [total] ([payees] to players, the rest to the bank).
     * Short of money: the current seat raises it in the DEBT phase; anyone
     * else (a card asking every player) sells what the bot would, else goes
     * bankrupt. True when paid now.
     */
    private fun charge(seat: Int, total: Int, payees: Map<Int, Int>, reason: MoneyReason, tile: Int?): Boolean {
        if (s.cashOf(seat) >= total) {
            pay(seat, total, payees, reason, tile)
            return true
        }
        if (seat == s.currentSeat) {
            s = s.copy(phase = CityBazaarPhase.DEBT, debt = CityBazaarDebt(total, payees, reason, tile))
            events += DebtStarted(seat, total)
            return false
        }
        raiseByBot(seat, total)
        if (s.cashOf(seat) >= total) pay(seat, total, payees, reason, tile) else bankrupt(seat, payees)
        return true
    }

    private fun pay(seat: Int, total: Int, payees: Map<Int, Int>, reason: MoneyReason, tile: Int?) {
        var cash = s.cash + (seat to s.cashOf(seat) - total)
        var bySeat = s.rentBySeat
        var byTile = s.rentByTile
        var paidBySeat = s.rentPaidBySeat
        payees.forEach { (to, amount) ->
            cash = cash + (to to (cash[to] ?: 0) + amount)
            events += MoneyMoved(seat, to, amount, reason, tile)
            // Rent totals for the result screen: what each owner and each tile earned.
            if (reason == MoneyReason.RENT) {
                bySeat = bySeat + (to to (bySeat[to] ?: 0) + amount)
                if (tile != null) byTile = byTile + (tile to (byTile[tile] ?: 0) + amount)
                paidBySeat = paidBySeat + (seat to (paidBySeat[seat] ?: 0) + amount)
            }
        }
        val toBank = total - payees.values.sum()
        if (toBank > 0) events += MoneyMoved(seat, null, toBank, reason, tile)
        s = s.copy(cash = cash, rentBySeat = bySeat, rentByTile = byTile, rentPaidBySeat = paidBySeat)
    }

    /** Sells to the bank (as the bot would) until [seat] has [amount] or nothing is left. */
    private fun raiseByBot(seat: Int, amount: Int) {
        while (s.cashOf(seat) < amount) {
            val (tile, building) = CityBazaarBot.nextSale(s, seat) ?: return
            if (building) sellBuilding(seat, tile) else sellTile(seat, tile)
        }
    }

    /** Buildings go in reverse: the hotel back to 3 houses, then house by house; half the build cost each. */
    fun sellBuilding(seat: Int, tile: Int): Boolean {
        if (s.owners[tile] != seat || s.level(tile) == 0) return false
        val amount = CityBazaarEconomy.buildingResale(tile)
        val level = s.level(tile) - 1
        s = s.copy(levels = s.levels + (tile to level), cash = s.cash + (seat to s.cashOf(seat) + amount))
        events += BuildingSold(seat, tile, level, amount)
        events += MoneyMoved(null, seat, amount, MoneyReason.SELL, tile)
        return true
    }

    /**
     * A bare tile back to the bank at half its price. A city with buildings
     * sells them first, one at a time (hotel → 3 houses → … → bare city);
     * other cities don't matter.
     */
    fun sellTile(seat: Int, tile: Int): Boolean {
        if (s.owners[tile] != seat || s.level(tile) > 0) return false
        val amount = CityBazaarEconomy.tileResale(tile)
        s = s.copy(owners = s.owners - tile, levels = s.levels - tile, cash = s.cash + (seat to s.cashOf(seat) + amount))
        events += TileSold(seat, tile, amount)
        events += MoneyMoved(null, seat, amount, MoneyReason.SELL, tile)
        return true
    }

    /** A bare tile up for auction: opens at its bank value (half the price). */
    fun startAuction(seat: Int, tile: Int): Boolean {
        if (s.owners[tile] != seat || s.level(tile) > 0) return false
        val opening = CityBazaarEconomy.tileResale(tile)
        s = s.copy(phase = CityBazaarPhase.AUCTION, auction = CityBazaarAuction(tile, seat, opening, null))
        events += AuctionStarted(seat, tile, opening)
        return true
    }

    /** [bidder] bids the next step (the opening amount if nobody has yet). */
    fun bid(bidder: Int): Boolean {
        val a = s.auction ?: return false
        if (bidder == a.seller || bidder !in s.active || bidder == a.leader) return false
        val amount = if (a.leader == null) a.bid else a.bid + BidSteps.after(a.bid)
        if (s.cashOf(bidder) < amount) return false
        s = s.copy(auction = a.copy(bid = amount, leader = bidder))
        events += BidPlaced(bidder, a.tile, amount)
        return true
    }

    /** Time's up: the highest bidder pays the seller and takes the tile (no backing out); no bids, the bank buys it at half. */
    fun closeAuction(): Boolean {
        val a = s.auction ?: return false
        val winner = a.leader
        if (winner != null) {
            s = s.copy(
                owners = s.owners + (a.tile to winner),
                cash = s.cash + (winner to s.cashOf(winner) - a.bid) + (a.seller to s.cashOf(a.seller) + a.bid),
            )
            events += MoneyMoved(winner, a.seller, a.bid, MoneyReason.AUCTION, a.tile)
        } else {
            sellTile(a.seller, a.tile)
        }
        events += AuctionEnded(a.seller, a.tile, winner, if (winner != null) a.bid else CityBazaarEconomy.tileResale(a.tile))
        s = s.copy(auction = null, phase = CityBazaarPhase.DEBT)
        settleDebtIfPossible()
        return true
    }

    /** In DEBT: pays once the balance covers it, then the turn moves on. */
    fun settleDebtIfPossible() {
        val debt = s.debt ?: return
        val seat = s.currentSeat
        if (s.cashOf(seat) < debt.total) return
        pay(seat, debt.total, debt.payees, debt.reason, debt.tile)
        s = s.copy(debt = null, phase = CityBazaarPhase.ROLL)
        settle()
    }

    /**
     * The player in debt gives up. With debt auctions on, every city they
     * still own goes up for auction first, one at a time (its houses sold
     * back to the bank), never straight to the bank; only once none is left
     * and the debt still isn't covered are they bankrupt.
     */
    fun giveUp(seat: Int, payees: Map<Int, Int>) {
        val next = s.owners.filter { it.value == seat }.keys.firstOrNull()
        if (s.cbRules.house.debtAuction && next != null) {
            while (s.level(next) > 0) sellBuilding(seat, next)
            // Selling the houses may already cover it: then the city stays theirs.
            settleDebtIfPossible()
            if (s.debt == null) return
            if (startAuction(seat, next)) return
        }
        bankrupt(seat, payees)
    }

    /** Everything goes to the bank; what's left of the cash goes to [payees]; out of the match. */
    fun bankrupt(seat: Int, payees: Map<Int, Int>) {
        s.owners.filter { it.value == seat }.keys.forEach { tile ->
            while (s.level(tile) > 0) sellBuilding(seat, tile)
            sellTile(seat, tile)
        }
        var left = s.cashOf(seat)
        var cash = s.cash
        payees.forEach { (to, amount) ->
            val give = minOf(left, amount)
            if (give > 0) {
                cash = cash + (to to (cash[to] ?: 0) + give)
                events += MoneyMoved(seat, to, give, MoneyReason.BANKRUPTCY, null)
                left -= give
            }
        }
        val wasCurrent = seat == s.currentSeat
        s = s.copy(
            cash = cash + (seat to 0),
            seats = s.seats.filter { it.value != seat },
            bankruptOrder = s.bankruptOrder + seat,
            jailed = s.jailed - seat,
            halfRent = s.halfRent - seat,
            skipNext = s.skipNext - seat,
            debt = if (wasCurrent) null else s.debt,
        )
        events += WentBankrupt(seat)
        if (s.active.size <= 1) return finish()
        if (wasCurrent) nextTurn()
    }

    // ---------------- decisions ----------------

    fun buy(seat: Int): Boolean {
        val tile = s.pendingTile ?: return false
        val price = CityBazaarBoard.tile(tile).price
        if (s.cashOf(seat) < price) return false
        s = s.copy(owners = s.owners + (tile to seat), cash = s.cash + (seat to s.cashOf(seat) - price))
        events += TileBought(seat, tile, price)
        events += MoneyMoved(seat, null, price, MoneyReason.BUY, tile)
        return true
    }

    fun build(seat: Int, tile: Int): Boolean {
        val cost = CityBazaarBoard.tile(tile).buildCost
        if (!CityBazaarEconomy.buildAllowed(s, seat, tile) || s.cashOf(seat) < cost) return false
        val level = s.level(tile) + 1
        s = s.copy(levels = s.levels + (tile to level), cash = s.cash + (seat to s.cashOf(seat) - cost))
        events += BuildingBuilt(seat, tile, level, cost)
        events += MoneyMoved(seat, null, cost, MoneyReason.BUILD, tile)
        return true
    }

    /** A BUY / BUILD decision is done: the turn moves on. */
    fun decided() {
        s = s.copy(phase = CityBazaarPhase.ROLL, pendingTile = null)
        settle()
    }

    // ---------------- turn order ----------------

    private fun settle() {
        if (ended || s.phase in WAITING) return
        nextTurn()
    }

    fun nextTurn() {
        ended = true
        val active = s.active.toSet()
        if (active.size <= 1) return finish()
        var seat = s.currentSeat
        var round = s.round
        var skip = s.skipNext
        while (true) {
            val next = (1..s.seatCount).map { (seat + it - 1) % s.seatCount + 1 }.first { it in active }
            if (next <= seat) round++
            seat = next
            if (seat in skip) {
                skip = skip - seat
                events += TurnSkipped(seat)
            } else {
                break
            }
        }
        // Jail was decided on the turn it happened: the next turn starts free.
        if (s.inJail(seat)) leaveJail(seat, "SERVED")
        s = s.copy(
            currentSeat = seat,
            round = round,
            skipNext = skip,
            // Half rent lasts until the player's own next turn.
            halfRent = s.halfRent - seat,
            phase = CityBazaarPhase.ROLL,
            jailTriesLeft = 0,
            pendingTile = null,
            debt = null,
            auction = null,
        )
        val limit = s.cbRules.house.roundLimit
        if (limit != null && round > limit) {
            // 2+ still in: they vote to keep going instead of the match just ending.
            if (s.active.size < 2) return finish()
            s = s.copy(phase = CityBazaarPhase.EXTENSION_VOTE, limitHit = CityBazaarLimit.ROUNDS)
            return
        }
        events += TurnPassed(seat)
    }

    // ---------------- at the limit ----------------

    /** The time is up (between turns): 2+ still in vote to go on, else it ends. */
    fun reachTimeLimit() {
        if (s.active.size < 2) return finish()
        s = s.copy(phase = CityBazaarPhase.EXTENSION_VOTE, limitHit = CityBazaarLimit.TIME)
        ended = true
    }

    /** They voted to keep going: more rounds or minutes, and play resumes. */
    fun extend(extraRounds: Int, extraMinutes: Int) {
        val house = s.cbRules.house
        val hit = s.limitHit
        val rules = when (hit) {
            CityBazaarLimit.ROUNDS -> house.copy(roundLimit = (house.roundLimit ?: s.round) + extraRounds)
            CityBazaarLimit.TIME -> house.copy(timeLimitMinutes = (house.timeLimitMinutes ?: 0) + extraMinutes)
            null -> house
        }
        s = s.copy(cbRules = s.cbRules.copy(house = rules), limitHit = null, phase = CityBazaarPhase.ROLL)
        ended = true
        // At the round limit the next player was already chosen: their turn starts now.
        if (hit == CityBazaarLimit.ROUNDS) events += TurnPassed(s.currentSeat)
    }

    fun finish() {
        ended = true
        s = s.copy(phase = CityBazaarPhase.FINISHED, pendingTile = null, debt = null, auction = null, limitHit = null)
        events += MatchEnded(CityBazaarEngine.ranking(s))
    }

    /** A seat leaves for good: everything goes back to the bank. */
    fun remove(seat: Int) {
        bankrupt(seat, emptyMap())
    }

    private companion object {
        val WAITING = setOf(
            CityBazaarPhase.BUY, CityBazaarPhase.BUILD, CityBazaarPhase.DEBT,
            CityBazaarPhase.AUCTION, CityBazaarPhase.JAIL_ROLL, CityBazaarPhase.JAIL_CHOICE, CityBazaarPhase.FINISHED,
        )
    }
}
