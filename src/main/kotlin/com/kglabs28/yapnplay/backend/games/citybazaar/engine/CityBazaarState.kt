package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import kotlinx.serialization.Serializable

import com.kglabs28.yapnplay.backend.games.base.identity.GameId
import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState
import com.kglabs28.yapnplay.backend.games.base.match.TurnState
import com.kglabs28.yapnplay.backend.games.base.rules.GameRules

/**
 * A City Bazaar match. [seats] are the players still in. [owners] tile →
 * seat; [levels] tile → 0 land, 1–3 houses, 4 hotel. [jailed] seats in
 * jail; [jailTriesLeft] rolls left this turn for the current seat;
 * [halfRent] seats collecting half rent until their next turn.
 */
@Serializable
data class CityBazaarState(
    override val gameId: GameId,
    val cbRules: CityBazaarRules,
    override val seats: List<SeatNumber>,
    val seatCount: Int,
    val positions: Map<Int, Int>,
    val cash: Map<Int, Int>,
    val owners: Map<Int, Int> = emptyMap(),
    val levels: Map<Int, Int> = emptyMap(),
    val currentSeat: Int,
    val phase: CityBazaarPhase,
    val die1: Int = 0,
    val die2: Int = 0,
    val jailed: Set<Int> = emptySet(),
    val jailTriesLeft: Int = 0,
    val halfRent: Set<Int> = emptySet(),
    val skipNext: Set<Int> = emptySet(),
    val pendingTile: Int? = null,
    val debt: CityBazaarDebt? = null,
    val auction: CityBazaarAuction? = null,
    val bankruptOrder: List<Int> = emptyList(),
    val round: Int = 1,
    val turnCounter: Long = 0,
    /** Rent each seat has collected this match (seat → ₹). */
    val rentBySeat: Map<Int, Int> = emptyMap(),
    /** Rent each tile has earned its owners this match (tile → ₹). */
    val rentByTile: Map<Int, Int> = emptyMap(),
    /** Rent each seat has paid to other players this match (rent only: not Club, Rest House or tax). */
    val rentPaidBySeat: Map<Int, Int> = emptyMap(),
    /** Which limit paused play (phase EXTENSION_VOTE) until the players decide to go on or end. */
    val limitHit: CityBazaarLimit? = null,
    /** Everything paid to the bank this match (₹). */
    val bankReceived: Long = 0,
    /** Everything the bank paid out this match (₹). */
    val bankPaid: Long = 0,
) : GameState {
    override val rules: GameRules get() = cbRules
    override val turn: TurnState get() = CityBazaarTurnState(SeatNumber(currentSeat), phase.name)

    val active: List<Int> get() = seats.map { it.value }

    fun cashOf(seat: Int): Int = cash[seat] ?: 0

    fun inJail(seat: Int): Boolean = seat in jailed

    fun level(tile: Int): Int = levels[tile] ?: 0
}
