package com.kglabs28.yapnplay.backend.domain.economy

/**
 * The emojis a player can send another seat for coins: one fixed set for
 * everyone, numbered "1" to "8" in picker order. The app decides which
 * emoji each number shows (so the art can change in an app update); the
 * prices are a setting (coins.emoji_gift_prices), one per number in this order.
 */
object EmojiGifts {
    val IDS: List<String> = (1..COUNT).map { it.toString() }

    /** What [id] costs with these [prices]; null for an id that is not on sale. */
    fun priceOf(id: String, prices: List<Int>): Int? = IDS.indexOf(id).takeIf { it >= 0 }?.let { prices.getOrNull(it) }

    private const val COUNT = 8
}
