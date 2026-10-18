package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/**
 * Luck and Shagun Chest, as in the original game: the dice total of the
 * roll that brought the player there (2–12) picks the result. Luck pays
 * out on odd totals and costs on even ones; Shagun Chest is the other way
 * round. The original tables aren't available in full, so the entries
 * follow the board's examples (docs/city-bazaar-rules.md §7).
 */
object CityBazaarCards {
    val luck: List<CardSpec> = listOf(
        CardSpec("luck-2", CardDeck.LUCK, 2, "Loss in share market", CardEffect.Money(-2_000)),
        CardSpec("luck-3", CardDeck.LUCK, 3, "Lottery prize", CardEffect.Money(2_500)),
        CardSpec("luck-4", CardDeck.LUCK, 4, "Fine for reckless driving", CardEffect.Money(-1_000)),
        CardSpec("luck-5", CardDeck.LUCK, 5, "Advance to START", CardEffect.AdvanceTo(START_TILE)),
        CardSpec("luck-6", CardDeck.LUCK, 6, "House repairs", CardEffect.Money(-1_500)),
        CardSpec("luck-7", CardDeck.LUCK, 7, "Crossword prize", CardEffect.Money(1_000)),
        CardSpec("luck-8", CardDeck.LUCK, 8, "Go to Jail", CardEffect.GoToJail),
        CardSpec("luck-9", CardDeck.LUCK, 9, "Advance to Mumbai", CardEffect.AdvanceTo(MUMBAI_TILE)),
        CardSpec("luck-10", CardDeck.LUCK, 10, "Treat everyone", CardEffect.PayEach(250)),
        CardSpec("luck-11", CardDeck.LUCK, 11, "Best employee of the year", CardEffect.Money(2_500)),
        CardSpec("luck-12", CardDeck.LUCK, 12, "Go to Rest House", CardEffect.AdvanceTo(REST_HOUSE_TILE)),
    )
    val chest: List<CardSpec> = listOf(
        CardSpec("chest-2", CardDeck.CHEST, 2, "Your birthday", CardEffect.CollectFromEach(500)),
        CardSpec("chest-3", CardDeck.CHEST, 3, "Go to Jail", CardEffect.GoToJail),
        CardSpec("chest-4", CardDeck.CHEST, 4, "Beauty contest winner", CardEffect.Money(2_500)),
        CardSpec("chest-5", CardDeck.CHEST, 5, "School fees", CardEffect.Money(-1_500)),
        CardSpec("chest-6", CardDeck.CHEST, 6, "Income tax refund", CardEffect.Money(2_000)),
        CardSpec("chest-7", CardDeck.CHEST, 7, "Insurance premium", CardEffect.Money(-1_500)),
        CardSpec("chest-8", CardDeck.CHEST, 8, "Sold old scrap", CardEffect.Money(1_000)),
        CardSpec("chest-9", CardDeck.CHEST, 9, "Medical bill", CardEffect.Money(-1_000)),
        CardSpec("chest-10", CardDeck.CHEST, 10, "Profit in share market", CardEffect.Money(3_000)),
        CardSpec("chest-11", CardDeck.CHEST, 11, "Property tax", CardEffect.PerProperty(PROPERTY_TAX_PER)),
        CardSpec("chest-12", CardDeck.CHEST, 12, "Wedding shagun", CardEffect.Money(1_500)),
    )

    fun deck(deck: CardDeck): List<CardSpec> = if (deck == CardDeck.LUCK) luck else chest

    /** The result for a dice [total] (2–12). */
    fun forRoll(deck: CardDeck, total: Int): CardSpec = deck(deck).first { it.roll == total }

    fun find(id: String): CardSpec? = (luck + chest).firstOrNull { it.id == id }

    /** Luck pays out on odd totals, Shagun Chest on even ones; the rest cost. */
    fun isGain(deck: CardDeck, total: Int): Boolean = (total % 2 == 1) == (deck == CardDeck.LUCK)
}
