package com.kglabs28.yapnplay.backend.games.mono.identity

import com.kglabs28.yapnplay.backend.games.base.rules.HouseRuleOption

object MonoConstants {
    const val ID = "mono"
    const val DISPLAY_NAME = "MONO"
    val TAGLINES = listOf("Match", "Play", "Win")

    const val PLAYERS_MIN = 2
    const val PLAYERS_MAX = 8
    const val QUICK_PLAY_SEATS = 4

    const val SEARCH_SECONDS = 30L
    const val TURN_SECONDS = 15L
    const val STARTING_HAND_SIZE = 7
    const val CALL_WINDOW_MS = 2500L

    enum class Color { RED, GREEN, BLUE, GOLD }

    enum class CardKind {
        NUMBER, REVERSE, SKIP, DRAW_TWO,
        PRISM_COLOR_WILD, PRISM_DRAW_FOUR,
    }

    val DECK_SIZE = 108

    val HOUSE_RULES = listOf(
        HouseRuleOption("stacking_allowed", "Stacking Allowed", "Stack +2 on +2", false),
        HouseRuleOption("seven_swap", "Seven Swap", "Playing a 7 swaps hands with an opponent", false),
        HouseRuleOption("zero_pass", "Zero Pass", "Playing a 0 passes all hands to the next player", false),
    )

    val FIXED_RULES = listOf(
        "2-8 players, 7 cards each",
        "Match by color, number, or symbol — or play any Prism card",
        "Prism Color Wild: choose the next color",
        "Prism +4: next player draws 4 and loses their turn",
        "MONO! callout: tap MONO! within 2.5s when down to 1 card",
        "Missed MONO!: draw +2 penalty cards",
        "15 seconds per turn; the bot plays when time runs out",
        "First to empty their hand wins",
    )
}
