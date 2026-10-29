package com.kglabs28.yapnplay.backend.games.citybazaar.utils

import com.kglabs28.yapnplay.backend.games.base.rules.HouseRuleOption

// Identity and showcase (shared with the server's game list).
const val CITY_BAZAAR_ID = "city-bazaar"
const val CITY_BAZAAR_DISPLAY_NAME = "City Royale"
val CITY_BAZAAR_TAGLINES = listOf("Buy", "Build", "Boss")

/** Rooms with friends: 3 to 6. Random (Quick Play) tables always seat 4. */
const val CITY_BAZAAR_MIN_PLAYERS = 3
const val CITY_BAZAAR_MAX_PLAYERS = 6
const val CITY_BAZAAR_QUICK_PLAY_SEATS = 4
const val CITY_BAZAAR_SEARCH_SECONDS = 15L
/** Seconds per decision before the turn plays itself. */
const val CITY_BAZAAR_TURN_SECONDS = 10L

// Money (₹). Starting cash and the limits are AppManager values; these are the defaults.
const val STARTING_CASH = 20_000
const val START_BONUS = 1_500
const val JAIL_FINE = 500
const val CLUB_FEE = 500
/** Club: the lander pays each other player this; Rest House: each other player pays the lander. */
const val CLUB_REST_EACH = 50
/** Property tax (a Shagun Chest result): per property owned. */
const val PROPERTY_TAX_PER = 50
/** Wealth Tax tile: per building owned (each house and each hotel). */
const val WEALTH_TAX_PER_BUILDING = 50
/** Taken when the turn timer rolls for someone who didn't roll and has Autoplay off. */
const val LATE_ROLL_FINE = 50
/** Selling to the bank pays half the price (tiles) or build cost (buildings). */
const val RESALE_DIVISOR = 2
/** Owning this many tiles of one colour doubles their rent (whatever it is). */
const val SET_FOR_DOUBLE = 3
/** Default round limit; the server setting CITY_BAZAAR_ROUND_LIMIT overrides it. */
const val ROUND_LIMIT = 30
const val TIME_LIMIT_MINUTES = 30
/** Rounds added when the players vote to keep going (server setting CITY_BAZAAR_EXTENSION_ROUNDS). */
const val EXTENSION_ROUNDS = 30
/** Minutes added when the players vote to keep going after the time limit. */
const val EXTENSION_MINUTES = 15
/** Seconds the keep-playing vote stays open. */
const val EXTENSION_VOTE_SECONDS = 10L
/** The bank's starting reserve (server setting CITY_BAZAAR_BANK_START): large enough never to run dry. */
const val BANK_START = 300_000
/** Auction: seconds to bid when it opens, and again after every bid; nobody bids in time and the highest bid wins. */
const val AUCTION_SECONDS = 10L

// Board
const val TILE_COUNT = 36
const val START_TILE = 0
const val JAIL_TILE = 9
const val CLUB_TILE = 18
const val REST_HOUSE_TILE = 27
const val MUMBAI_TILE = 1
/** Building level of a hotel (after 3 houses). */
const val HOTEL_LEVEL = 4
const val DICE_FACES = 6
/** Rolls for doubles allowed each turn in jail. */
const val JAIL_TRIES = 3
const val BACK_STEPS = 3

// Bot (CPU seats): money it keeps after buying or building, and when it pays its way out of jail.
const val BOT_BUY_RESERVE = 2_000
const val BOT_SET_BUY_RESERVE = 500
const val BOT_BUILD_RESERVE = 3_000
const val BOT_JAIL_FINE_CASH = 6_000

// Lobby: house-rule toggles (keys match CityBazaarHouseRules) and the fixed rule sheet.
val CITY_BAZAAR_HOUSE_RULES = listOf(
    HouseRuleOption("set_double_rent", "Colour Bonus", "3 cities of one colour: their rent doubles", true),
    HouseRuleOption("rest_house_skip", "Rest House Treat", "Land on Rest House: every other player pays you ₹50", true),
    HouseRuleOption("club_fee", "Club Party", "Land on Club: you pay every other player ₹50", true),
    HouseRuleOption("debt_auction", "Auction to Pay Debts", "In debt, a city you sell goes up for auction instead of straight to the bank", true),
    HouseRuleOption("money_popups", "Money Popups", "Show a popup for every payment (off: a quick coin animation)", true),
    HouseRuleOption("round_limit", "Round Limit", "End after the set number of rounds; richest wins", true),
    HouseRuleOption("time_limit", "Time Limit", "End when the match time is up; richest wins", true),
)

val CITY_BAZAAR_FIXED_RULES = listOf(
    "Rooms 3–6 players, random tables 4",
    "₹1,500 every time you pass or land on START",
    "Land on your own city to build: up to 3 houses, then a hotel",
    "Jail: right away, roll for doubles (3 tries), pay ₹500 or take half rent",
    "Short of money: sell buildings, then sell or auction cities",
    "10 seconds per decision; a missed roll costs ₹50",
)
