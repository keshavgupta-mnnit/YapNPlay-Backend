package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/** Auction bid steps grow with the price: +₹50 under ₹1,000, +₹100 under ₹3,000, +₹250 under ₹6,000, then +₹500. */
object BidSteps {
    fun after(bid: Int): Int = when {
        bid < SMALL -> STEP_SMALL
        bid < MEDIUM -> STEP_MEDIUM
        bid < LARGE -> STEP_LARGE
        else -> STEP_TOP
    }

    private const val SMALL = 1_000
    private const val MEDIUM = 3_000
    private const val LARGE = 6_000
    private const val STEP_SMALL = 50
    private const val STEP_MEDIUM = 100
    private const val STEP_LARGE = 250
    private const val STEP_TOP = 500
}
