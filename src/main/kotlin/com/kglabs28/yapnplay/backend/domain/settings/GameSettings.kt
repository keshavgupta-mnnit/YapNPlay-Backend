package com.kglabs28.yapnplay.backend.domain.settings

import com.kglabs28.yapnplay.backend.domain.economy.EmojiGifts

/** The admin "Game settings" sub-tab a setting shows on, in tab order. */
enum class SettingGroup(val label: String) {
    MONO("MONO"),
    CHAOS("Complete Chaos"),
    ROYAL_LUDO("Royal Ludo"),
    CITY_BAZAAR("City Royale"),
    DOODLE_KING("Doodle King"),
    CYBER_BLAST("Cyber Blast"),
    ECONOMY("Coins & ads"),
    XP("XP and level"),
    MATCH("All matches"),
}

/** A value the operator can change from the admin dashboard without a deploy. */
sealed class GameSetting<T>(
    val key: String,
    val group: SettingGroup,
    val label: String,
    val default: T,
) {
    /** Null when [value] is allowed, else what is wrong with it. */
    abstract fun problem(value: T): String?
}

class IntSetting(
    key: String,
    group: SettingGroup,
    label: String,
    default: Int,
    val min: Int,
    val max: Int,
) : GameSetting<Int>(key, group, label, default) {
    override fun problem(value: Int): String? = if (value in min..max) null else "Must be between $min and $max"
}

class IntListSetting(
    key: String,
    group: SettingGroup,
    label: String,
    default: List<Int>,
    /** What the list must look like, for the admin page. */
    val hint: String,
    private val rule: (List<Int>) -> String?,
) : GameSetting<List<Int>>(key, group, label, default) {
    override fun problem(value: List<Int>): String? = rule(value)
}

/** Every runtime setting, with the value used when nobody changed it. */
object GameSettings {
    private const val MAX_COINS = 1_000_000
    private const val PERCENT = 100
    private const val LEAGUE_COUNT = 5
    private const val MAX_SECONDS = 120
    private const val MAX_PAUSE_MS = 30_000

    /** League names, in fee order; the fees are a setting. */
    val LEAGUE_NAMES = listOf("Starter", "Bronze", "Silver", "Gold", "Diamond")

    // ── Coins ─────────────────────────────────────────────────────────────
    val DAILY_BONUS = IntSetting("coins.daily_bonus", SettingGroup.ECONOMY, "Daily bonus (coins)", 100, 0, MAX_COINS)
    val AD_REWARDS = IntListSetting(
        "coins.ad_rewards", SettingGroup.ECONOMY, "Coins for the 1st, 2nd, 3rd… rewarded ad of the day",
        listOf(200, 300, 400, 500, 700), "One amount per ad; ads past the end pay the last one",
    ) { rewards ->
        when {
            rewards.isEmpty() -> "Needs at least one amount"
            rewards.any { it !in 0..MAX_COINS } -> "Each between 0 and $MAX_COINS"
            else -> null
        }
    }
    val AD_DAILY_LIMIT = IntSetting("coins.ad_daily_limit", SettingGroup.ECONOMY, "Rewarded ads per day", 5, 0, 50)
    val AD_COOLDOWN_MINUTES = IntSetting("coins.ad_cooldown_minutes", SettingGroup.ECONOMY, "Minutes between paid rewarded ads", 60, 0, 1_440)
    val LEAGUE_FEES = IntListSetting(
        "coins.league_fees", SettingGroup.ECONOMY, "Quick Play league fees (Starter, Bronze, Silver, Gold, Diamond)",
        listOf(0, 100, 500, 1_000, 5_000), "5 rising numbers, the first 0",
    ) { fees ->
        when {
            fees.size != LEAGUE_COUNT -> "Needs exactly $LEAGUE_COUNT fees"
            fees.first() != 0 -> "Starter must be free (0)"
            fees.zipWithNext().any { (a, b) -> b <= a } -> "Each league must cost more than the one before"
            fees.last() > MAX_COINS -> "At most $MAX_COINS"
            else -> null
        }
    }
    val EMOJI_GIFT_PRICES = IntListSetting(
        "coins.emoji_gift_prices", SettingGroup.ECONOMY,
        "Emoji gift prices for emojis 1 to ${EmojiGifts.IDS.size}, in picker order",
        listOf(100, 100, 100, 200, 200, 200, 300, 300, 300), "One price per emoji, ${EmojiGifts.IDS.size} numbers, e.g. [100,100,100,200,200,200,300,300,300]",
    ) { prices ->
        when {
            prices.size != EmojiGifts.IDS.size -> "Needs exactly ${EmojiGifts.IDS.size} prices"
            prices.any { it !in 1..MAX_COINS } -> "Each between 1 and $MAX_COINS"
            else -> null
        }
    }
    val COMPLETION_COINS = IntSetting("coins.completion", SettingGroup.ECONOMY, "Coins for finishing a match (every league)", 100, 0, MAX_COINS)
    val COMPLETION_DAILY_CAP = IntSetting("coins.completion_daily_cap", SettingGroup.ECONOMY, "Paid finishes per player per day", 10, 0, 100)
    val COMPLETION_MIN_MINUTES = IntSetting("coins.completion_min_minutes", SettingGroup.ECONOMY, "Shortest match that pays finishing coins (minutes)", 3, 0, 60)

    /** Pot share (%) by finishing place, one setting per table size. */
    val POT_SPLITS: Map<Int, IntListSetting> = mapOf(
        2 to listOf(100),
        3 to listOf(60, 40),
        4 to listOf(50, 30, 20),
        5 to listOf(40, 30, 20, 10),
        6 to listOf(35, 25, 20, 12, 8),
    ).mapValues { (seats, default) ->
        IntListSetting("pot.split.$seats", SettingGroup.ECONOMY, "Pot split % for $seats players (1st first)", default, "Up to $seats numbers adding up to 100 at most") { split ->
            when {
                split.isEmpty() || split.size > seats -> "Give 1 to $seats numbers"
                split.any { it < 0 } -> "No negative shares"
                split.sum() > PERCENT -> "Shares add up to more than 100"
                else -> null
            }
        }
    }

    // ── XP ────────────────────────────────────────────────────────────────
    val XP_PER_PLAY = IntSetting("xp.per_play", SettingGroup.XP, "XP for finishing a match", 100, 0, 10_000)
    val XP_PLACE_BONUS = IntListSetting(
        "xp.place_bonus", SettingGroup.XP, "Fixed extra XP by end position (1st first; places past the list get none)", listOf(400, 300, 200, 150, 100, 50, 25, 0), "Up to 8 numbers, none negative",
    ) { bonus -> if (bonus.size > 8 || bonus.any { it !in 0..10_000 }) "Up to 8 numbers from 0 to 10000" else null }
    val XP_PER_MINUTE = IntSetting("xp.per_minute", SettingGroup.XP, "Variable XP for every minute the match ran", 10, 0, 1_000)
    val XP_TIME_CAP_MINUTES = IntSetting("xp.time_cap_minutes", SettingGroup.XP, "Minutes past this give no more time XP (250 at 10 a minute: a match gives up to about 3000 XP)", 250, 1, 600)
    val XP_PER_LEVEL = IntSetting("xp.per_level", SettingGroup.XP, "XP per level", 1_000, 100, 1_000_000)

    // ── Every match ───────────────────────────────────────────────────────
    val VOTE_SECONDS = IntSetting("match.vote_seconds", SettingGroup.MATCH, "Keep-playing vote (seconds)", 10, 5, MAX_SECONDS)
    val TURN_GAP_MS = IntSetting("match.turn_gap_ms", SettingGroup.MATCH, "Pause between one player's turn ending (its animation done) and the next turn starting (ms)", 1_000, 0, 10_000)
    val TURN_FOLLOW_UP_SECONDS = IntSetting("match.turn_follow_up_seconds", SettingGroup.MATCH, "Least time for a follow-up step in the same turn (seconds)", 5, 1, MAX_SECONDS)
    val END_VOTE_SECONDS = IntSetting("match.end_vote_seconds", SettingGroup.MATCH, "End-game vote (seconds)", 20, 5, MAX_SECONDS)
    val END_VOTE_COOLDOWN_MINUTES = IntSetting("match.end_vote_cooldown_minutes", SettingGroup.MATCH, "Minutes before a player may ask to end again", 3, 0, 60)
    val BOT_STANDING_MIN_PCT = IntSetting("bot.standing_min_pct", SettingGroup.MATCH, "Bots (all games): risk weight % when far behind the leader", 60, 0, 300)
    val BOT_STANDING_MAX_PCT = IntSetting("bot.standing_max_pct", SettingGroup.MATCH, "Bots (all games): risk weight % when leading", 140, 0, 300)
    val QUICK_PLAY_SEARCH_SECONDS = IntSetting("match.quick_play_search_seconds", SettingGroup.MATCH, "Quick Play: time to find players before CPUs fill (seconds)", 10, 5, MAX_SECONDS)
    val START_WAIT_SECONDS = IntSetting("match.start_wait_seconds", SettingGroup.MATCH, "Longest wait for everyone to open the table (seconds)", 20, 0, MAX_SECONDS)

    // ── City Bazaar ───────────────────────────────────────────────────────
    val CB_TURN_SECONDS = IntSetting("cb.turn_seconds", SettingGroup.CITY_BAZAAR, "Turn time (seconds)", 10, 5, MAX_SECONDS)
    val CB_DEBT_SECONDS = IntSetting("cb.debt_seconds", SettingGroup.CITY_BAZAAR, "Time to choose what to sell or auction for a debt (seconds)", 30, 10, MAX_SECONDS)
    val CB_ROUND_LIMIT = IntSetting("cb.round_limit", SettingGroup.CITY_BAZAAR, "Round limit: the keep-playing vote comes at this round, or at the time limit, whichever is first", 60, 1, 500)
    val CB_TIME_LIMIT_MINUTES = IntSetting("cb.time_limit_minutes", SettingGroup.CITY_BAZAAR, "Time limit: the first keep-playing vote comes after this many minutes (or at the round limit)", 45, 1, 600)
    val CB_VOTE_GAP_MINUTES = IntSetting("cb.vote_gap_minutes", SettingGroup.CITY_BAZAAR, "Least minutes between two keep-playing votes (a limit reached sooner extends quietly)", 15, 0, 240)
    val CB_EXTENSION_ROUNDS = IntSetting("cb.extension_rounds", SettingGroup.CITY_BAZAAR, "Rounds added by a keep-playing vote", 30, 1, 500)
    val CB_EXTENSION_MINUTES = IntSetting("cb.extension_minutes", SettingGroup.CITY_BAZAAR, "Minutes added by a keep-playing vote", 15, 1, 240)
    val CB_START_CASH = IntSetting("cb.start_cash", SettingGroup.CITY_BAZAAR, "Each player's starting money, up to 4 players (₹)", 20_000, 1_000, 1_000_000)
    val CB_START_CASH_LARGE = IntSetting("cb.start_cash_large", SettingGroup.CITY_BAZAAR, "Each player's starting money, 5 or more players (₹)", 15_000, 1_000, 1_000_000)
    val CB_BANK_START = IntSetting("cb.bank_start", SettingGroup.CITY_BAZAAR, "Bank's starting money (₹)", 300_000, 0, 100_000_000)
    val CB_CLUB_REST_EACH = IntSetting("cb.club_rest_each", SettingGroup.CITY_BAZAAR, "Club / Rest House amount per player (₹)", 50, 0, 100_000)
    val CB_MESSAGE_PAUSE_MS = IntSetting("cb.message_pause_ms", SettingGroup.CITY_BAZAAR, "Reading time per table message (ms)", 2_500, 0, 10_000)
    val CB_MESSAGE_PAUSE_CAP_MS = IntSetting("cb.message_pause_cap_ms", SettingGroup.CITY_BAZAAR, "Most reading time per update (ms)", 8_000, 0, MAX_PAUSE_MS)
    val CB_CPU_THINK_MS = IntSetting("cb.cpu_think_ms", SettingGroup.CITY_BAZAAR, "Bots: least think time before each decision (ms)", 2_000, 0, 10_000)
    val CB_CPU_THINK_JITTER_MS = IntSetting("cb.cpu_think_jitter_ms", SettingGroup.CITY_BAZAAR, "Bots: extra random think time, up to (ms); 2000 + 3000 = 2-5 s", 3_000, 0, 10_000)
    val CB_CPU_BID_DELAY_MS = IntSetting("cb.cpu_bid_delay_ms", SettingGroup.CITY_BAZAAR, "Bots: least wait before bidding again in an auction (ms)", 1_200, 0, 10_000)
    val CB_CPU_FIRST_BID_DELAY_MS = IntSetting("cb.cpu_first_bid_delay_ms", SettingGroup.CITY_BAZAAR, "Bots: least wait before the first bid on a new auction, so people can bid first (ms)", 3_000, 0, 10_000)
    val CB_CPU_DECISION_EXTRA_MS = IntSetting("cb.cpu_decision_extra_ms", SettingGroup.CITY_BAZAAR, "Bots: extra time for a real choice (buy, build, jail, debt) (ms); 0 keeps every decision within 2-5 s", 0, 0, 10_000)
    val CB_CPU_RESERVE = IntSetting("cb.cpu_reserve", SettingGroup.CITY_BAZAAR, "CPU: least cash kept after paying (₹)", 2_000, 0, 100_000)
    val CB_CPU_SAFETY_BUFFER = IntSetting("cb.bot_safety_buffer", SettingGroup.CITY_BAZAAR, "CPU: cushion on top of the expected bills (₹)", 1_000, 0, 100_000)
    val CB_AUTO_BUY_RESERVE = IntSetting("cb.auto_buy_reserve", SettingGroup.CITY_BAZAAR, "Autoplay: least cash kept after paying (₹)", 500, 0, 100_000)
    val CB_AUTO_SAFETY_BUFFER = IntSetting("cb.auto_safety_buffer", SettingGroup.CITY_BAZAAR, "Autoplay: cushion on top of the expected bills (₹)", 500, 0, 100_000)
    val CB_BOT_RUIN_WEIGHT_PCT = IntSetting("cb.bot_ruin_weight_pct", SettingGroup.CITY_BAZAAR, "Bots: weight of the risk of going broke (chance × net worth), %", 200, 0, 1_000)
    val CB_BOT_DENIAL_WEIGHT_PCT = IntSetting("cb.bot_denial_weight_pct", SettingGroup.CITY_BAZAAR, "Bots: value of keeping a city from a rival it helps, % of their gain", 100, 0, 500)
    val CB_BOT_RUIN_TARGET_PCT = IntSetting("cb.bot_ruin_target_pct", SettingGroup.CITY_BAZAAR, "Bots: cash kept so the chance of going broke in 2 turns stays under this %", 10, 0, 100)
    val CB_BOT_PRESSURE_VALUE = IntSetting("cb.bot_pressure_value", SettingGroup.CITY_BAZAAR, "Bots: value of a house a rival who can't pay may land on soon", 2_000, 0, 100_000)
    val CB_BOT_COMFORT_PCT = IntSetting("cb.bot_comfort_pct", SettingGroup.CITY_BAZAAR, "Bots: plenty of cash = this % of keep-cash (jail: pay at once)", 200, 100, 1_000)
    val CB_BOT_JAIL_POSTPONE_PCT = IntSetting("cb.bot_jail_postpone_pct", SettingGroup.CITY_BAZAAR, "Bots: % of the rent dodged in jail that is really saved", 30, 0, 100)
    val CB_BOT_DEFAULT_ROUNDS = IntSetting("cb.bot_default_rounds", SettingGroup.CITY_BAZAAR, "Bots: rounds assumed left with no round limit", 20, 3, 30)

    // ── MONO and MONO: Complete Chaos ──
    val MONO_TURN_SECONDS = IntSetting("mono.turn_seconds", SettingGroup.MONO, "Turn time (seconds)", 15, 5, MAX_SECONDS)
    val MONO_CPU_THINK_MS = IntSetting("mono.cpu_think_ms", SettingGroup.MONO, "Bots: least think time before each decision (ms)", 2_000, 0, 10_000)
    val MONO_CPU_THINK_JITTER_MS = IntSetting("mono.cpu_think_jitter_ms", SettingGroup.MONO, "Bots: extra random think time, up to (ms); 2000 + 3000 = 2-5 s", 3_000, 0, 10_000)
    val MONO_CPU_DECISION_EXTRA_MS = IntSetting("mono.cpu_decision_extra_ms", SettingGroup.MONO, "Bots: extra time for a real choice (colour, stack, swap) (ms); 0 keeps every decision within 2-5 s", 0, 0, 10_000)
    val MONO_PENALTY_CARDS = IntSetting("mono.penalty_cards", SettingGroup.MONO, "Cards drawn when caught without calling MONO!", 5, 0, 10)
    val MONO_FALSE_CATCH_CARDS = IntSetting("mono.false_catch_cards", SettingGroup.MONO, "Cards the catcher draws for a wrong catch", 2, 0, 10)
    val CHAOS_CPU_THINK_MS = IntSetting("chaos.cpu_think_ms", SettingGroup.CHAOS, "Bots: least think time before each decision (ms)", 2_000, 0, 10_000)
    val CHAOS_CPU_THINK_JITTER_MS = IntSetting("chaos.cpu_think_jitter_ms", SettingGroup.CHAOS, "Bots: extra random think time, up to (ms); 2000 + 3000 = 2-5 s", 3_000, 0, 10_000)
    val CHAOS_CPU_DECISION_EXTRA_MS = IntSetting("chaos.cpu_decision_extra_ms", SettingGroup.CHAOS, "Bots: extra time for a real choice (colour, roulette, stack, swap) (ms); 0 keeps every decision within 2-5 s", 0, 0, 10_000)
    val CHAOS_PENALTY_CARDS = IntSetting("chaos.penalty_cards", SettingGroup.CHAOS, "Cards drawn when caught without calling MONO!", 7, 0, 15)
    val CHAOS_FALSE_CATCH_CARDS = IntSetting("chaos.false_catch_cards", SettingGroup.CHAOS, "Cards the catcher draws for a wrong catch", 2, 0, 15)
    val CHAOS_KNOCKOUT_CARDS = IntSetting("chaos.knockout_cards", SettingGroup.CHAOS, "A hand this big knocks the player out", 25, 10, 100)
    val MONO_BOT_CARD_COST = IntSetting("mono.bot_card_cost", SettingGroup.MONO, "Bots: what one card in hand costs", 10, 1, 100)
    val MONO_BOT_SHED_PCT = IntSetting("mono.bot_shed_pct", SettingGroup.MONO, "Bots: shed high cards early, % of card points when a rival is near MONO", 100, 0, 500)
    val MONO_BOT_ATTACK_VALUE = IntSetting("mono.bot_attack_value", SettingGroup.MONO, "Bots: value per penalty card on a next player close to winning", 8, 0, 100)
    val MONO_BOT_HOLD_VALUE = IntSetting("mono.bot_hold_value", SettingGroup.MONO, "Bots: value of keeping a wild or draw card back", 15, 0, 200)
    val MONO_BOT_COLOUR_VALUE = IntSetting("mono.bot_colour_value", SettingGroup.MONO, "Bots: value of keeping a colour the hand can follow", 10, 0, 100)
    val MONO_BOT_CATCH_REACTION_MS = IntSetting("mono.bot_catch_reaction_ms", SettingGroup.MONO, "Bots: reaction time before pressing Caught (ms)", 1_500, 0, 10_000)
    val MONO_BOT_KNOCKOUT_COST = IntSetting("mono.bot_knockout_cost", SettingGroup.MONO, "Bots (Chaos): cost of being knocked out", 1_000, 0, 10_000)

    // ── Royal Ludo ────────────────────────────────────────────────────────
    val RL_TURN_SECONDS = IntSetting("rl.turn_seconds", SettingGroup.ROYAL_LUDO, "Turn time (seconds)", 10, 5, MAX_SECONDS)
    val RL_CPU_THINK_MS = IntSetting("rl.cpu_think_ms", SettingGroup.ROYAL_LUDO, "Bots: least think time before each decision (ms)", 2_000, 0, 10_000)
    val RL_CPU_THINK_JITTER_MS = IntSetting("rl.cpu_think_jitter_ms", SettingGroup.ROYAL_LUDO, "Bots: extra random think time, up to (ms); 2000 + 3000 = 2-5 s", 3_000, 0, 10_000)
    val RL_CPU_DECISION_EXTRA_MS = IntSetting("rl.cpu_decision_extra_ms", SettingGroup.ROYAL_LUDO, "Bots: extra time for a real choice (which piece to move) (ms); 0 keeps every decision within 2-5 s", 0, 0, 10_000)
    val RL_SIX_BOOST_PER_MISS_PCT = IntSetting("ludo.six_boost_per_miss_pct", SettingGroup.ROYAL_LUDO, "All pieces in base: each roll without a six adds this many % to the next six's chance (0 = fair dice)", 8, 0, 30)
    val RL_SIX_CHANCE_CAP_PCT = IntSetting("ludo.six_chance_cap_pct", SettingGroup.ROYAL_LUDO, "All pieces in base: the chance of a six never goes above this %", 50, 17, 90)
    val RL_BOT_BASE_COST = IntSetting("ludo.bot_base_cost", SettingGroup.ROYAL_LUDO, "Bots: what a piece in play is worth beyond its progress (squares)", 30, 0, 200)
    val RL_BOT_CAPTURE_WEIGHT_PCT = IntSetting("ludo.bot_capture_weight_pct", SettingGroup.ROYAL_LUDO, "Bots: capture value, % of the victim's worth", 200, 0, 1_000)
    val RL_BOT_EXTRA_TURN = IntSetting("ludo.bot_extra_turn", SettingGroup.ROYAL_LUDO, "Bots: value of the extra turn a capture gives", 10, 0, 200)
    val RL_BOT_FINISH = IntSetting("ludo.bot_finish", SettingGroup.ROYAL_LUDO, "Bots: value of bringing a piece home", 200, 0, 2_000)
    val RL_BOT_LEAVE_BASE = IntSetting("ludo.bot_leave_base", SettingGroup.ROYAL_LUDO, "Bots: value of leaving base (when not landing in danger)", 40, 0, 500)
    val RL_BOT_RISK_WEIGHT_PCT = IntSetting("ludo.bot_risk_weight_pct", SettingGroup.ROYAL_LUDO, "Bots: weight of the change in hit risk, %", 300, 0, 2_000)
    val RL_BOT_FLEX = IntSetting("ludo.bot_flex", SettingGroup.ROYAL_LUDO, "Bots: early-game value of having another piece out", 20, 0, 200)
    val RL_BOT_ENDGAME_CAPTURE_PCT = IntSetting("ludo.bot_endgame_capture_pct", SettingGroup.ROYAL_LUDO, "Bots: capture value % against the rival leading the race", 150, 0, 500)
    val RL_BOT_CHASE = IntSetting("ludo.bot_chase", SettingGroup.ROYAL_LUDO, "Bots: value per opponent piece 1-6 squares ahead it could hit", 5, 0, 100)

    // ── Doodle King ───────────────────────────────────────────────────────
    val DK_ROUNDS = IntSetting("dk.rounds", SettingGroup.DOODLE_KING, "Rounds (everyone draws once a round; words easy, medium, then hard)", 3, 1, 6)
    val DK_DRAW_SECONDS = IntSetting("dk.draw_seconds", SettingGroup.DOODLE_KING, "Drawing time per turn (seconds)", 60, 20, 180)
    val DK_INTRO_SECONDS = IntSetting("dk.intro_seconds", SettingGroup.DOODLE_KING, "\"Your turn\" before the word pick (seconds)", 3, 1, 10)
    val DK_PICK_SECONDS = IntSetting("dk.pick_seconds", SettingGroup.DOODLE_KING, "Time to pick 1 of 3 words (seconds; then one is picked at random)", 10, 3, 30)
    val DK_REVEAL_SECONDS = IntSetting("dk.reveal_seconds", SettingGroup.DOODLE_KING, "The word shown after a turn (seconds)", 5, 2, 15)
    val DK_GUESS_POINTS = IntListSetting(
        "dk.guess_points", SettingGroup.DOODLE_KING, "Points for guessing, 1st to guess first (later guessers get the last number)", listOf(100, 80, 65, 50, 40, 30, 25, 20), "1 to 8 numbers from 0 to 1000",
    ) { points -> if (points.isEmpty() || points.size > 8 || points.any { it !in 0..1_000 }) "1 to 8 numbers from 0 to 1000" else null }
    val DK_LEVEL_POINTS_PCT = IntListSetting(
        "dk.level_points_pct", SettingGroup.DOODLE_KING, "Points by word level, % (easy, medium, hard): guessing and drawing points both scale", listOf(100, 150, 200), "3 numbers from 10 to 1000",
    ) { pct -> if (pct.size != 3 || pct.any { it !in 10..1_000 }) "3 numbers from 10 to 1000" else null }
    val DK_DRAWER_PER_GUESS = IntSetting("dk.drawer_per_guess", SettingGroup.DOODLE_KING, "Drawer's points for each correct guess", 25, 0, 500)
    val DK_ALL_GUESSED_BONUS = IntSetting("dk.all_guessed_bonus", SettingGroup.DOODLE_KING, "Drawer's bonus when everyone guesses", 30, 0, 500)
    val DK_EXTENSION_ROUNDS = IntSetting("dk.extension_rounds", SettingGroup.DOODLE_KING, "Rounds added when the table votes to keep playing", 1, 1, 3)
    val DK_PASS_PENALTY = IntSetting("dk.pass_penalty", SettingGroup.DOODLE_KING, "Points lost for passing or drawing nothing", 50, 0, 500)
    val DK_HINT_START_PCT = IntSetting("dk.hint_start_pct", SettingGroup.DOODLE_KING, "Letter hints start after this % of the drawing time", 40, 0, 95)
    // TODO before the live release: back to 0 (or set 0 on the admin board). On while Doodle King is being tested.
    val DK_TEST_BOTS = IntSetting("dk.test_bots", SettingGroup.DOODLE_KING, "TESTING ONLY: 1 lets CPU players fill Doodle King tables (set 0 live)", 1, 0, 1)

    // ── Cyber Blast ───────────────────────────────────────────────────────
    val BLAST_FRAME_MS = IntSetting("blast.frame_ms", SettingGroup.CYBER_BLAST, "Board updates sent every (ms; lower is smoother but more data)", 50, 30, 200)
    val BLAST_LIVES = IntSetting("blast.lives", SettingGroup.CYBER_BLAST, "Hearts per player", 5, 1, 10)
    val BLAST_START_RADIUS = IntSetting("blast.start_radius", SettingGroup.CYBER_BLAST, "Blast reach at the start, and after losing a heart (tiles)", 1, 1, 6)
    val BLAST_MAX_RADIUS = IntSetting("blast.max_radius", SettingGroup.CYBER_BLAST, "Longest blast reach (tiles)", 7, 1, 14)
    val BLAST_RADIUS_PER_HIT = IntSetting("blast.radius_per_hit", SettingGroup.CYBER_BLAST, "Reach added for each player hit in a row (tiles)", 1, 0, 3)
    val BLAST_BOMBS = IntSetting("blast.bombs", SettingGroup.CYBER_BLAST, "Bombs one player may have down at once (0: no limit)", 0, 0, 20)
    val BLAST_FUSE_MS = IntSetting("blast.fuse_ms", SettingGroup.CYBER_BLAST, "Bomb fuse (ms)", 3_000, 1_000, 6_000)
    val BLAST_FLAME_MS = IntSetting("blast.flame_ms", SettingGroup.CYBER_BLAST, "Fire stays on the board (ms)", 500, 200, 2_000)
    val BLAST_SPEED = IntSetting("blast.speed", SettingGroup.CYBER_BLAST, "Walking speed (thousandths of a tile a second)", 4_000, 1_000, 8_000)
    val BLAST_BOOST_SPEED = IntSetting("blast.boost_speed", SettingGroup.CYBER_BLAST, "Speed with the boots (thousandths of a tile a second)", 5_600, 1_000, 10_000)
    val BLAST_BOOST_SECONDS = IntSetting("blast.boost_seconds", SettingGroup.CYBER_BLAST, "Speed boots last (seconds)", 30, 5, 120)
    val BLAST_SHIELD_MS = IntSetting("blast.shield_ms", SettingGroup.CYBER_BLAST, "Can't be hurt after coming back (ms)", 2_000, 0, 10_000)
    val BLAST_HIT_POINTS = IntSetting("blast.hit_points", SettingGroup.CYBER_BLAST, "Points for hitting another player", 100, 0, 1_000)
    val BLAST_HIT_PENALTY = IntSetting("blast.hit_penalty", SettingGroup.CYBER_BLAST, "Points lost when someone else's bomb hits you", 25, 0, 1_000)
    val BLAST_SELF_PENALTY = IntSetting("blast.self_penalty", SettingGroup.CYBER_BLAST, "Points lost to your own bomb", 15, 0, 1_000)
    val BLAST_CRATE_POINTS = IntSetting("blast.crate_points", SettingGroup.CYBER_BLAST, "Points for each blocker broken", 10, 0, 500)
    val BLAST_CRATE_DENSITY_PCT = IntSetting("blast.crate_density_pct", SettingGroup.CYBER_BLAST, "Free tiles that start with a blocker, usual amount (%)", 60, 0, 90)
    val BLAST_DENSITY_FEW_PCT = IntSetting("blast.density_few_pct", SettingGroup.CYBER_BLAST, "Room option Fewer Blockers: free tiles with a blocker (%)", 35, 0, 90)
    val BLAST_DENSITY_MANY_PCT = IntSetting("blast.density_many_pct", SettingGroup.CYBER_BLAST, "Room option More Blockers: free tiles with a blocker (%)", 80, 0, 90)
    val BLAST_DENSITY_PER_PLAYER_PCT = IntSetting("blast.density_per_player_pct", SettingGroup.CYBER_BLAST, "Blockers: % fewer for each player over 4 (more for each under)", 4, 0, 20)
    val BLAST_BIG_CLOSE_AFTER_SECONDS = IntSetting("blast.big_close_after_seconds", SettingGroup.CYBER_BLAST, "Big Arena: closing in starts after (seconds; 0 never)", 300, 0, 1_800)
    val BLAST_ITEM_EVERY_SECONDS = IntSetting("blast.item_every_seconds", SettingGroup.CYBER_BLAST, "Speed boots show up every (seconds, at least)", 12, 3, 120)
    val BLAST_ITEM_JITTER_SECONDS = IntSetting("blast.item_jitter_seconds", SettingGroup.CYBER_BLAST, "…plus up to (seconds, at random)", 8, 0, 120)
    val BLAST_ITEM_LIFE_SECONDS = IntSetting("blast.item_life_seconds", SettingGroup.CYBER_BLAST, "Speed boots lie on the board for (seconds)", 12, 3, 120)
    val BLAST_MAX_ITEMS = IntSetting("blast.max_items", SettingGroup.CYBER_BLAST, "Most speed boots on the board at once", 2, 0, 6)
    val BLAST_COUNTDOWN_SECONDS = IntSetting("blast.countdown_seconds", SettingGroup.CYBER_BLAST, "Countdown before anyone may move (seconds)", 3, 0, 10)
    val BLAST_CLOSE_AFTER_SECONDS = IntSetting("blast.close_after_seconds", SettingGroup.CYBER_BLAST, "The arena starts closing in after (seconds; 0 never)", 180, 0, 1_800)
    val BLAST_CLOSE_STEP_MS = IntSetting("blast.close_step_ms", SettingGroup.CYBER_BLAST, "Closing arena: one tile every (ms)", 600, 100, 5_000)
    val BLAST_BOT_REACT_MS = IntSetting("blast.bot_react_ms", SettingGroup.CYBER_BLAST, "Bots: reaction time, at least (ms)", 180, 50, 2_000)
    val BLAST_BOT_REACT_JITTER_MS = IntSetting("blast.bot_react_jitter_ms", SettingGroup.CYBER_BLAST, "Bots: reaction time, plus up to (ms, at random)", 220, 0, 2_000)
    val BLAST_BOT_BOMB_GAP_MS = IntSetting("blast.bot_bomb_gap_ms", SettingGroup.CYBER_BLAST, "Bots: least time between their bombs (ms)", 1_200, 0, 10_000)
    val BLAST_BOT_BOMB_GAP_JITTER_MS = IntSetting("blast.bot_bomb_gap_jitter_ms", SettingGroup.CYBER_BLAST, "Bots: …plus up to (ms, at random)", 1_300, 0, 10_000)
    val BLAST_BOT_LIFE_VALUE = IntSetting("blast.bot_life_value", SettingGroup.CYBER_BLAST, "Bots: what losing a heart is worth (points)", 300, 0, 5_000)
    val BLAST_BOT_STEP_COST = IntSetting("blast.bot_step_cost", SettingGroup.CYBER_BLAST, "Bots: cost of each step walked (points)", 4, 0, 100)
    val BLAST_BOT_HIT_CHANCE_PCT = IntSetting("blast.bot_hit_chance_pct", SettingGroup.CYBER_BLAST, "Bots: chance a player in a bomb's line gets caught (%)", 30, 0, 100)
    val BLAST_BOT_CORNER_BONUS_PCT = IntSetting("blast.bot_corner_bonus_pct", SettingGroup.CYBER_BLAST, "Bots: extra chance when that player is boxed in (%)", 40, 0, 100)
    val BLAST_BOT_BOOST_VALUE = IntSetting("blast.bot_boost_value", SettingGroup.CYBER_BLAST, "Bots: worth of the speed boots (points)", 60, 0, 1_000)
    val BLAST_BOT_HUNT_VALUE = IntSetting("blast.bot_hunt_value", SettingGroup.CYBER_BLAST, "Bots: worth of each step closer to a player (points; more than a step costs, so idle bots go hunting)", 8, 0, 100)
    val BLAST_BOT_THREAT_PCT = IntSetting("blast.bot_threat_pct", SettingGroup.CYBER_BLAST, "Bots: risk of standing in a player's line, each (%)", 12, 0, 100)
    val BLAST_BOT_PLANNED_BOMB_PCT = IntSetting("blast.bot_planned_bomb_pct", SettingGroup.CYBER_BLAST, "Bots: a spot is worth this % of the bomb they could drop there", 70, 0, 100)

    val ALL: List<GameSetting<*>> = listOf(
        DAILY_BONUS, AD_REWARDS, AD_DAILY_LIMIT, AD_COOLDOWN_MINUTES, LEAGUE_FEES, EMOJI_GIFT_PRICES, COMPLETION_COINS, COMPLETION_DAILY_CAP, COMPLETION_MIN_MINUTES,
    ) + POT_SPLITS.values + listOf(
        XP_PER_PLAY, XP_PLACE_BONUS, XP_PER_MINUTE, XP_TIME_CAP_MINUTES, XP_PER_LEVEL,
        VOTE_SECONDS, TURN_GAP_MS, TURN_FOLLOW_UP_SECONDS, START_WAIT_SECONDS, QUICK_PLAY_SEARCH_SECONDS, END_VOTE_SECONDS, END_VOTE_COOLDOWN_MINUTES,
        BOT_STANDING_MIN_PCT, BOT_STANDING_MAX_PCT,
        CB_TURN_SECONDS, CB_DEBT_SECONDS, CB_CPU_BID_DELAY_MS, CB_CPU_FIRST_BID_DELAY_MS, CB_ROUND_LIMIT, CB_TIME_LIMIT_MINUTES, CB_VOTE_GAP_MINUTES, CB_EXTENSION_ROUNDS, CB_EXTENSION_MINUTES, CB_START_CASH, CB_START_CASH_LARGE, CB_BANK_START,
        CB_CLUB_REST_EACH, CB_MESSAGE_PAUSE_MS, CB_MESSAGE_PAUSE_CAP_MS, CB_CPU_THINK_MS, CB_CPU_THINK_JITTER_MS, CB_CPU_DECISION_EXTRA_MS,
        CB_CPU_RESERVE, CB_CPU_SAFETY_BUFFER, CB_AUTO_BUY_RESERVE, CB_AUTO_SAFETY_BUFFER,
        CB_BOT_RUIN_WEIGHT_PCT, CB_BOT_DENIAL_WEIGHT_PCT, CB_BOT_RUIN_TARGET_PCT, CB_BOT_PRESSURE_VALUE, CB_BOT_COMFORT_PCT, CB_BOT_JAIL_POSTPONE_PCT, CB_BOT_DEFAULT_ROUNDS,
        MONO_TURN_SECONDS, MONO_CPU_THINK_MS, MONO_CPU_THINK_JITTER_MS, MONO_CPU_DECISION_EXTRA_MS,
        CHAOS_CPU_THINK_MS, CHAOS_CPU_THINK_JITTER_MS, CHAOS_CPU_DECISION_EXTRA_MS, MONO_PENALTY_CARDS, CHAOS_PENALTY_CARDS,
        CHAOS_KNOCKOUT_CARDS, MONO_BOT_CARD_COST, MONO_BOT_SHED_PCT, MONO_BOT_ATTACK_VALUE, MONO_BOT_HOLD_VALUE, MONO_BOT_COLOUR_VALUE, MONO_BOT_KNOCKOUT_COST, MONO_BOT_CATCH_REACTION_MS,
        RL_TURN_SECONDS, RL_CPU_THINK_MS, RL_CPU_THINK_JITTER_MS, RL_CPU_DECISION_EXTRA_MS, RL_SIX_BOOST_PER_MISS_PCT, RL_SIX_CHANCE_CAP_PCT,
        RL_BOT_BASE_COST, RL_BOT_CAPTURE_WEIGHT_PCT, RL_BOT_EXTRA_TURN, RL_BOT_FINISH, RL_BOT_LEAVE_BASE, RL_BOT_RISK_WEIGHT_PCT, RL_BOT_CHASE, RL_BOT_FLEX, RL_BOT_ENDGAME_CAPTURE_PCT,
        DK_ROUNDS, DK_DRAW_SECONDS, DK_INTRO_SECONDS, DK_PICK_SECONDS, DK_REVEAL_SECONDS, DK_GUESS_POINTS, DK_LEVEL_POINTS_PCT, DK_DRAWER_PER_GUESS,
        DK_ALL_GUESSED_BONUS, DK_PASS_PENALTY, DK_HINT_START_PCT, DK_EXTENSION_ROUNDS, DK_TEST_BOTS,
        BLAST_FRAME_MS, BLAST_LIVES, BLAST_START_RADIUS, BLAST_MAX_RADIUS, BLAST_RADIUS_PER_HIT, BLAST_BOMBS, BLAST_FUSE_MS, BLAST_FLAME_MS,
        BLAST_SPEED, BLAST_BOOST_SPEED, BLAST_BOOST_SECONDS, BLAST_SHIELD_MS, BLAST_HIT_POINTS, BLAST_HIT_PENALTY, BLAST_SELF_PENALTY,
        BLAST_CRATE_POINTS, BLAST_CRATE_DENSITY_PCT, BLAST_DENSITY_FEW_PCT, BLAST_DENSITY_MANY_PCT, BLAST_DENSITY_PER_PLAYER_PCT, BLAST_BIG_CLOSE_AFTER_SECONDS, BLAST_ITEM_EVERY_SECONDS, BLAST_ITEM_JITTER_SECONDS, BLAST_ITEM_LIFE_SECONDS, BLAST_MAX_ITEMS,
        BLAST_COUNTDOWN_SECONDS, BLAST_CLOSE_AFTER_SECONDS, BLAST_CLOSE_STEP_MS,
        BLAST_BOT_REACT_MS, BLAST_BOT_REACT_JITTER_MS, BLAST_BOT_BOMB_GAP_MS, BLAST_BOT_BOMB_GAP_JITTER_MS, BLAST_BOT_LIFE_VALUE, BLAST_BOT_STEP_COST,
        BLAST_BOT_HIT_CHANCE_PCT, BLAST_BOT_CORNER_BONUS_PCT, BLAST_BOT_BOOST_VALUE, BLAST_BOT_HUNT_VALUE, BLAST_BOT_THREAT_PCT, BLAST_BOT_PLANNED_BOMB_PCT,
    )

    fun find(key: String): GameSetting<*>? = ALL.firstOrNull { it.key == key }
}

/** Reads the current value of a setting (changes apply to matches started after them). */
interface Settings {
    operator fun get(setting: IntSetting): Int
    operator fun get(setting: IntListSetting): List<Int>

    /** The built-in values (tests, and before the database is read). */
    object Defaults : Settings {
        override fun get(setting: IntSetting): Int = setting.default
        override fun get(setting: IntListSetting): List<Int> = setting.default
    }
}

/** Pot shares by table size, as currently set. */
fun Settings.potShares(): Map<Int, List<Int>> = GameSettings.POT_SPLITS.mapValues { this[it.value] }

/** Saved setting values (JSON text by key). */
interface SettingsRepository {
    suspend fun loadAll(): Map<String, String>
    suspend fun save(key: String, value: String, at: Long, by: String)
}
