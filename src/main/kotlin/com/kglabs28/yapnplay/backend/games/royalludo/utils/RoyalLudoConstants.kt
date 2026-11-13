package com.kglabs28.yapnplay.backend.games.royalludo.utils

import com.kglabs28.yapnplay.backend.games.base.rules.HouseRuleOption

// Identity and showcase (shared with the server's game list).
const val ROYAL_LUDO_ID = "royal-ludo"
const val ROYAL_LUDO_DISPLAY_NAME = "Royal Ludo"
val ROYAL_LUDO_TAGLINES = listOf("Roll", "Race", "Rule")

/**
 * Royal Ludo plays 4 to 8: a room's players pick the board (up to 4 the square board, 5–6 the
 * hexagon, 7–8 the octagon; see RoyalLudoTrack) and CPU fill tops up to that board's size. Below
 * 4 a room needs CPU fill. Quick Play always makes the admin's table size (ludo.quick_play_seats), CPUs filling it.
 */
const val ROYAL_LUDO_PLAYERS = 4
const val ROYAL_LUDO_MAX_PLAYERS = 8

/** What an app reports (with its games) when it can draw the 6- and 8-player boards. */
const val ROYAL_LUDO_BIG_BOARDS_FEATURE = "royal-ludo-8"

/** A random (Quick Play) Royal Ludo match fills all four seats. */
const val ROYAL_LUDO_QUICK_PLAY_SEATS = ROYAL_LUDO_PLAYERS

/** Quick Play search window before empty seats fill with CPU players. */
const val ROYAL_LUDO_SEARCH_SECONDS = 15L

/** Seconds per turn before the bot plays it. */
const val ROYAL_LUDO_TURN_SECONDS = 10L

const val PROGRESS_IN_BASE = -1
const val HOME_COLUMN_LENGTH = 5
const val PIECES_PER_COLOUR = 4
/** Pieces each on the 6- and 8-player boards with the "3 Pieces" house rule. */
const val PIECES_ON_BIG_BOARDS = 3
const val MAX_CONSECUTIVE_SIXES = 3
const val DICE_FACES = 6

// Lobby: house-rule toggles (keys match RoyalLudoHouseRules fields) and
// the fixed rule sheet. Defaults are the traditional rules.
val ROYAL_LUDO_HOUSE_RULES = listOf(
    HouseRuleOption("extra_turn_on_six", "Extra Turn on 6", "Roll again after a six", true),
    HouseRuleOption("extra_turn_on_capture", "Extra Turn on Capture", "Roll again after sending a piece home", true),
    HouseRuleOption("extra_turn_on_home", "Extra Turn on Finish", "Roll again when a piece reaches home", true),
    HouseRuleOption("three_sixes_forfeit", "Three Sixes Forfeit", "A third six in a row undoes the whole turn", true),
    HouseRuleOption("pair_protection", "Pair Protection", "Two pieces together can't be captured", true),
    HouseRuleOption("auto_move_single_option", "Auto Move", "Move automatically when only one move exists", true),
    HouseRuleOption("three_pieces", "3 Pieces (5–8 players)", "On the 6- and 8-player boards each player has 3 pieces instead of 4", false),
)

val ROYAL_LUDO_FIXED_RULES = listOf(
    "4 to 8 players: square board up to 4, hexagon for 5–6, octagon for 7–8; 4 pieces each",
    "A piece leaves base only on a 6",
    "Exact roll needed to reach home",
    "Landing on a lone opponent sends it back to base",
    "Start squares and star squares are safe",
    "10 seconds per turn; the bot plays when time runs out",
    "Away after 3 missed turns or 60 s offline: the seat is removed",
)
