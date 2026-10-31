package com.kglabs28.yapnplay.backend.games.monochaos.identity

import com.kglabs28.yapnplay.backend.games.base.rules.HouseRuleOption

object MonoChaosConstants {
    const val ID = "mono-chaos"
    const val DISPLAY_NAME = "MONO: Complete Chaos"
    val TAGLINES = listOf("Chaos", "Stack", "Survive")

    const val PLAYERS_MIN = 2
    const val PLAYERS_MAX = 8
    const val QUICK_PLAY_SEATS = 4

    const val SEARCH_SECONDS = 30L
    const val TURN_SECONDS = 15L
    const val STARTING_HAND_SIZE = 7
    const val CALL_WINDOW_MS = 2500L
    const val KNOCKOUT_THRESHOLD = 25

    val DECK_SIZE = 168

    enum class Color { RED, GREEN, BLUE, GOLD }

    enum class CardKind {
        NUMBER, REVERSE, SKIP, DRAW_TWO,
        COLORED_DRAW_FOUR, DISCARD_ALL, SKIP_EVERYONE,
        PRISM_COLOR_WILD, PRISM_DRAW_FOUR, PRISM_REVERSE_DRAW_FOUR,
        PRISM_DRAW_SIX, PRISM_DRAW_TEN, PRISM_COLOR_ROULETTE,
    }

    val DECK_SIZE_CHECK = 168

    val HOUSE_RULES = listOf(
        HouseRuleOption("seven_swap", "Seven Swap", "Playing a 7 swaps hands with an opponent", true),
        HouseRuleOption("zero_pass", "Zero Pass", "Playing a 0 passes all hands to the next player", true),
        HouseRuleOption("knockout_enabled", "25-Card Knockout", "Eliminate players at 25+ cards", true),
    )

    val FIXED_RULES = listOf(
        "2-8 players, 7 cards each",
        "Match by color, number, or symbol — or play any Prism card",
        "Rule of 7: playing a 7 swaps hands with an opponent",
        "Rule of 0: playing a 0 passes all hands to the next player",
        "Penalty stacking: stack +2, +4, +6, +10 on draw cards",
        "25-Card Knockout: 25+ cards eliminates you",
        "Draw until playable: draw cards until you can play (max 25)",
        "Call MONO! on your turn while holding 2 cards, before playing",
        "CAUGHT!: anyone can catch a player on 1 card who didn't call, until the next player's turn ends",
        "Caught: draw 7. A wrong catch: the catcher draws 4",
        "Play goes on after the first player finishes; from the 2nd finisher (3rd in rooms of 5+) the table votes to keep playing or end",
        "15 seconds per turn; rank by finishing order, then fewest cards; knocked-out players last",
    )
}
