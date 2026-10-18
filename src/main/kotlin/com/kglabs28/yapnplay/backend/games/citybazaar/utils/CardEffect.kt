package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/** What a Luck or Shagun Chest result does. */
sealed interface CardEffect {
    /** Gain (positive) or pay the bank (negative). */
    data class Money(val amount: Int) : CardEffect
    /** Move forward to [tile] (₹1,500 if passing START). */
    data class AdvanceTo(val tile: Int) : CardEffect
    data object GoToJail : CardEffect
    data class MoveBack(val steps: Int) : CardEffect
    data class CollectFromEach(val amount: Int) : CardEffect
    data class PayEach(val amount: Int) : CardEffect
    /** Property tax: [amount] for every property owned, to the bank. */
    data class PerProperty(val amount: Int) : CardEffect
}
