package com.kglabs28.yapnplay.backend.contract.citybazaar

import kotlinx.serialization.Serializable

/**
 * `MatchSnapshot.publicState` for City Bazaar. [owners] tile → seat,
 * [levels] tile → 0 land, 1–3 houses, 4 hotel. Times are epoch millis.
 */
@Serializable
data class CityBazaarPublicState(
    val players: List<CityBazaarSeatState>,
    val owners: Map<Int, Int>,
    val levels: Map<Int, Int>,
    /** ROLL, JAIL_ROLL, JAIL_CHOICE, BUY, BUILD, DEBT, AUCTION or FINISHED. */
    val phase: String,
    val die1: Int,
    val die2: Int,
    val pendingTile: Int? = null,
    val debt: CityBazaarDebt? = null,
    val jailTriesLeft: Int = 0,
    val auction: CityBazaarAuction? = null,
    /** When the match time limit ends; null when off. */
    val timeEndsAt: Long? = null,
    val round: Int,
    val roundLimit: Int? = null,
    val netWorth: Map<Int, Int>,
    /** Seats from first to last once the match is over. */
    val ranking: List<Int> = emptyList(),
    /** 0.4.2: rent each tile has earned its owners this match (tile → ₹). */
    val rentCollected: Map<Int, Int> = emptyMap(),
    /** 0.4.4: the bank's balance and its totals this match. */
    val bank: CityBazaarBank? = null,
    /** 0.5.0: house rule "debt_auction": in debt, selling a city starts an auction (label it "Auction"); false: sold straight to the bank. */
    val debtAuction: Boolean = true,
)

/** 0.4.4: the bank's [cash] = starting reserve + [received] − [paid] (₹). */
@Serializable
data class CityBazaarBank(
    val cash: Long,
    val received: Long,
    val paid: Long,
)

/** One seat's money and position. */
@Serializable
data class CityBazaarSeatState(
    val seat: Int,
    val cash: Int,
    val position: Int,
    val inJail: Boolean,
    val halfRent: Boolean,
    val bankrupt: Boolean,
    /** 0.4.2: rent this player has collected this match (₹). */
    val rentCollected: Int = 0,
    /** 0.5.0: rent this player has paid other players this match (₹; rent only, not Club, Rest House or tax). */
    val rentPaid: Int = 0,
)

/** What the current player owes but can't pay yet. [reason] is a MoneyReason name. */
@Serializable
data class CityBazaarDebt(val amount: Int, val reason: String, val tile: Int? = null)

/** A running auction: highest [bid] by [leaderSeat] (null: no bid yet, it opens at [bid]). */
@Serializable
data class CityBazaarAuction(
    val tile: Int,
    val sellerSeat: Int,
    val bid: Int,
    val leaderSeat: Int? = null,
    val nextBid: Int,
    val endsAt: Long,
)
