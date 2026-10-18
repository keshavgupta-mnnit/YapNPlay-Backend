package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import kotlinx.serialization.Serializable

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.MoneyReason

/**
 * Money the current seat owes but can't pay yet: [total], of which
 * [payees] (seat → amount) go to players and the rest to the bank.
 */
@Serializable
data class CityBazaarDebt(val total: Int, val payees: Map<Int, Int>, val reason: MoneyReason, val tile: Int?)
