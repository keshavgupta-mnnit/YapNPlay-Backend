package com.kglabs28.yapnplay.backend.domain.settings

/** The admin "Game settings" sub-tab a setting shows on, in tab order. */
enum class SettingGroup(val label: String) {
    MONO("MONO"),
    CHAOS("Complete Chaos"),
    ROYAL_LUDO("Royal Ludo"),
    CITY_BAZAAR("City Royale"),
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
    val AD_COOLDOWN_MINUTES = IntSetting("coins.ad_cooldown_minutes", SettingGroup.ECONOMY, "Minutes between paid rewarded ads", 120, 0, 1_440)
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
    val GROUP_FEE = IntSetting("coins.group_fee", SettingGroup.ECONOMY, "Room game fee per player (coins)", 100, 0, MAX_COINS)
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
        "xp.place_bonus", SettingGroup.XP, "Extra XP by place (1st first)", listOf(300, 200, 100), "Up to 6 numbers, none negative",
    ) { bonus -> if (bonus.size > 6 || bonus.any { it !in 0..10_000 }) "Up to 6 numbers from 0 to 10000" else null }
    val XP_REFERENCE_MINUTES = IntSetting("xp.reference_minutes", SettingGroup.XP, "A match this long gives the full XP; shorter less, longer more (minutes)", 10, 1, 120)
    val XP_MIN_TIME_FACTOR_PCT = IntSetting("xp.min_time_factor_pct", SettingGroup.XP, "Shortest matches still give this % of the XP", 50, 0, 100)
    val XP_MAX_TIME_FACTOR_PCT = IntSetting("xp.max_time_factor_pct", SettingGroup.XP, "Long matches give at most this % of the XP (1200 = up to 2 hours keeps counting)", 1_200, 100, 5_000)
    val XP_PER_LEVEL = IntSetting("xp.per_level", SettingGroup.XP, "XP per level", 1_000, 100, 1_000_000)

    // ── Every match ───────────────────────────────────────────────────────
    val VOTE_SECONDS = IntSetting("match.vote_seconds", SettingGroup.MATCH, "Keep-playing vote (seconds)", 10, 5, MAX_SECONDS)
    val TURN_FOLLOW_UP_SECONDS = IntSetting("match.turn_follow_up_seconds", SettingGroup.MATCH, "Least time for a follow-up step in the same turn (seconds)", 5, 1, MAX_SECONDS)
    val END_VOTE_SECONDS = IntSetting("match.end_vote_seconds", SettingGroup.MATCH, "End-game vote (seconds)", 20, 5, MAX_SECONDS)
    val END_VOTE_COOLDOWN_MINUTES = IntSetting("match.end_vote_cooldown_minutes", SettingGroup.MATCH, "Minutes before a player may ask to end again", 3, 0, 60)
    val BOT_STANDING_MIN_PCT = IntSetting("bot.standing_min_pct", SettingGroup.MATCH, "Bots (all games): risk weight % when far behind the leader", 60, 0, 300)
    val BOT_STANDING_MAX_PCT = IntSetting("bot.standing_max_pct", SettingGroup.MATCH, "Bots (all games): risk weight % when leading", 140, 0, 300)
    val QUICK_PLAY_SEARCH_SECONDS = IntSetting("match.quick_play_search_seconds", SettingGroup.MATCH, "Quick Play: time to find players before CPUs fill (seconds)", 10, 5, MAX_SECONDS)
    val START_WAIT_SECONDS = IntSetting("match.start_wait_seconds", SettingGroup.MATCH, "Longest wait for everyone to open the table (seconds)", 20, 0, MAX_SECONDS)

    // ── City Bazaar ───────────────────────────────────────────────────────
    val CB_TURN_SECONDS = IntSetting("cb.turn_seconds", SettingGroup.CITY_BAZAAR, "Turn time (seconds)", 10, 5, MAX_SECONDS)
    val CB_ROUND_LIMIT = IntSetting("cb.round_limit", SettingGroup.CITY_BAZAAR, "Quick Play round limit", 5, 1, 500)
    val CB_EXTENSION_ROUNDS = IntSetting("cb.extension_rounds", SettingGroup.CITY_BAZAAR, "Rounds added by a keep-playing vote", 30, 1, 500)
    val CB_EXTENSION_MINUTES = IntSetting("cb.extension_minutes", SettingGroup.CITY_BAZAAR, "Minutes added by a keep-playing vote", 15, 1, 240)
    val CB_BANK_START = IntSetting("cb.bank_start", SettingGroup.CITY_BAZAAR, "Bank's starting money (₹)", 300_000, 0, 100_000_000)
    val CB_CLUB_REST_EACH = IntSetting("cb.club_rest_each", SettingGroup.CITY_BAZAAR, "Club / Rest House amount per player (₹)", 50, 0, 100_000)
    val CB_MESSAGE_PAUSE_MS = IntSetting("cb.message_pause_ms", SettingGroup.CITY_BAZAAR, "Reading time per table message (ms)", 2_500, 0, 10_000)
    val CB_MESSAGE_PAUSE_CAP_MS = IntSetting("cb.message_pause_cap_ms", SettingGroup.CITY_BAZAAR, "Most reading time per update (ms)", 8_000, 0, MAX_PAUSE_MS)
    val CB_CPU_THINK_MS = IntSetting("cb.cpu_think_ms", SettingGroup.CITY_BAZAAR, "Bots: think time before each move (ms)", 1_200, 0, 10_000)
    val CB_CPU_THINK_JITTER_MS = IntSetting("cb.cpu_think_jitter_ms", SettingGroup.CITY_BAZAAR, "Bots: extra random think time, up to (ms)", 1_300, 0, 10_000)
    val CB_CPU_DECISION_EXTRA_MS = IntSetting("cb.cpu_decision_extra_ms", SettingGroup.CITY_BAZAAR, "Bots: extra time for a real choice (buy, build, jail, debt) (ms)", 1_000, 0, 10_000)
    val CB_CPU_RESERVE = IntSetting("cb.cpu_reserve", SettingGroup.CITY_BAZAAR, "CPU: least cash kept after paying (₹)", 2_000, 0, 100_000)
    val CB_CPU_SAFETY_BUFFER = IntSetting("cb.bot_safety_buffer", SettingGroup.CITY_BAZAAR, "CPU: cushion on top of the expected bills (₹)", 1_000, 0, 100_000)
    val CB_AUTO_BUY_RESERVE = IntSetting("cb.auto_buy_reserve", SettingGroup.CITY_BAZAAR, "Autoplay: least cash kept after paying (₹)", 500, 0, 100_000)
    val CB_AUTO_SAFETY_BUFFER = IntSetting("cb.auto_safety_buffer", SettingGroup.CITY_BAZAAR, "Autoplay: cushion on top of the expected bills (₹)", 500, 0, 100_000)
    val CB_BOT_RUIN_WEIGHT_PCT = IntSetting("cb.bot_ruin_weight_pct", SettingGroup.CITY_BAZAAR, "Bots: weight of the risk of going broke (chance × net worth), %", 100, 0, 1_000)
    val CB_BOT_DENIAL_WEIGHT_PCT = IntSetting("cb.bot_denial_weight_pct", SettingGroup.CITY_BAZAAR, "Bots: value of keeping a city from a rival it helps, % of their gain", 100, 0, 500)
    val CB_BOT_RUIN_TARGET_PCT = IntSetting("cb.bot_ruin_target_pct", SettingGroup.CITY_BAZAAR, "Bots: cash kept so the chance of going broke in 2 turns stays under this %", 10, 0, 100)
    val CB_BOT_PRESSURE_VALUE = IntSetting("cb.bot_pressure_value", SettingGroup.CITY_BAZAAR, "Bots: value of a house a rival who can't pay may land on soon", 2_000, 0, 100_000)
    val CB_BOT_COMFORT_PCT = IntSetting("cb.bot_comfort_pct", SettingGroup.CITY_BAZAAR, "Bots: plenty of cash = this % of keep-cash (jail: pay at once)", 200, 100, 1_000)
    val CB_BOT_JAIL_POSTPONE_PCT = IntSetting("cb.bot_jail_postpone_pct", SettingGroup.CITY_BAZAAR, "Bots: % of the rent dodged in jail that is really saved", 30, 0, 100)
    val CB_BOT_DEFAULT_ROUNDS = IntSetting("cb.bot_default_rounds", SettingGroup.CITY_BAZAAR, "Bots: rounds assumed left with no round limit", 20, 3, 30)

    // ── MONO and MONO: Complete Chaos ──
    val MONO_TURN_SECONDS = IntSetting("mono.turn_seconds", SettingGroup.MONO, "Turn time (seconds)", 15, 5, MAX_SECONDS)
    val MONO_CALL_WINDOW_MS = IntSetting("mono.call_window_ms", SettingGroup.MONO, "Time to call MONO! after going down to one card (ms)", 2_500, 500, 10_000)
    val MONO_DRAW_PLAY_WINDOW_MS = IntSetting("mono.draw_play_window_ms", SettingGroup.MONO, "Time to play a playable drawn card before the turn passes (ms)", 3_000, 500, 15_000)
    val MONO_CPU_THINK_MS = IntSetting("mono.cpu_think_ms", SettingGroup.MONO, "Bots: think time before each move (ms)", 1_200, 0, 10_000)
    val MONO_CPU_THINK_JITTER_MS = IntSetting("mono.cpu_think_jitter_ms", SettingGroup.MONO, "Bots: extra random think time, up to (ms)", 1_300, 0, 10_000)
    val MONO_CPU_DECISION_EXTRA_MS = IntSetting("mono.cpu_decision_extra_ms", SettingGroup.MONO, "Bots: extra time for a real choice (colour, stack, swap) (ms)", 800, 0, 10_000)
    val MONO_PENALTY_CARDS = IntSetting("mono.penalty_cards", SettingGroup.MONO, "Cards drawn when caught without calling MONO!", 5, 0, 10)
    val MONO_FALSE_CATCH_CARDS = IntSetting("mono.false_catch_cards", SettingGroup.MONO, "Cards the catcher draws for a wrong catch", 2, 0, 10)
    val CHAOS_CPU_THINK_MS = IntSetting("chaos.cpu_think_ms", SettingGroup.CHAOS, "Bots: think time before each move (ms)", 1_200, 0, 10_000)
    val CHAOS_CPU_THINK_JITTER_MS = IntSetting("chaos.cpu_think_jitter_ms", SettingGroup.CHAOS, "Bots: extra random think time, up to (ms)", 1_300, 0, 10_000)
    val CHAOS_CPU_DECISION_EXTRA_MS = IntSetting("chaos.cpu_decision_extra_ms", SettingGroup.CHAOS, "Bots: extra time for a real choice (colour, roulette, stack, swap) (ms)", 800, 0, 10_000)
    val CHAOS_PENALTY_CARDS = IntSetting("chaos.penalty_cards", SettingGroup.CHAOS, "Cards drawn when caught without calling MONO!", 7, 0, 15)
    val CHAOS_FALSE_CATCH_CARDS = IntSetting("chaos.false_catch_cards", SettingGroup.CHAOS, "Cards the catcher draws for a wrong catch", 4, 0, 15)
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
    val RL_CPU_THINK_MS = IntSetting("rl.cpu_think_ms", SettingGroup.ROYAL_LUDO, "Bots: think time before each move (ms)", 1_200, 0, 10_000)
    val RL_CPU_THINK_JITTER_MS = IntSetting("rl.cpu_think_jitter_ms", SettingGroup.ROYAL_LUDO, "Bots: extra random think time, up to (ms)", 1_300, 0, 10_000)
    val RL_CPU_DECISION_EXTRA_MS = IntSetting("rl.cpu_decision_extra_ms", SettingGroup.ROYAL_LUDO, "Bots: extra time for a real choice (which piece to move) (ms)", 500, 0, 10_000)
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

    val ALL: List<GameSetting<*>> = listOf(
        DAILY_BONUS, AD_REWARDS, AD_DAILY_LIMIT, AD_COOLDOWN_MINUTES, LEAGUE_FEES, GROUP_FEE, COMPLETION_COINS, COMPLETION_DAILY_CAP, COMPLETION_MIN_MINUTES,
    ) + POT_SPLITS.values + listOf(
        XP_PER_PLAY, XP_PLACE_BONUS, XP_REFERENCE_MINUTES, XP_MIN_TIME_FACTOR_PCT, XP_MAX_TIME_FACTOR_PCT, XP_PER_LEVEL,
        VOTE_SECONDS, TURN_FOLLOW_UP_SECONDS, START_WAIT_SECONDS, QUICK_PLAY_SEARCH_SECONDS, END_VOTE_SECONDS, END_VOTE_COOLDOWN_MINUTES,
        BOT_STANDING_MIN_PCT, BOT_STANDING_MAX_PCT,
        CB_TURN_SECONDS, CB_ROUND_LIMIT, CB_EXTENSION_ROUNDS, CB_EXTENSION_MINUTES, CB_BANK_START,
        CB_CLUB_REST_EACH, CB_MESSAGE_PAUSE_MS, CB_MESSAGE_PAUSE_CAP_MS, CB_CPU_THINK_MS, CB_CPU_THINK_JITTER_MS, CB_CPU_DECISION_EXTRA_MS,
        CB_CPU_RESERVE, CB_CPU_SAFETY_BUFFER, CB_AUTO_BUY_RESERVE, CB_AUTO_SAFETY_BUFFER,
        CB_BOT_RUIN_WEIGHT_PCT, CB_BOT_DENIAL_WEIGHT_PCT, CB_BOT_RUIN_TARGET_PCT, CB_BOT_PRESSURE_VALUE, CB_BOT_COMFORT_PCT, CB_BOT_JAIL_POSTPONE_PCT, CB_BOT_DEFAULT_ROUNDS,
        MONO_TURN_SECONDS, MONO_CALL_WINDOW_MS, MONO_DRAW_PLAY_WINDOW_MS, MONO_CPU_THINK_MS, MONO_CPU_THINK_JITTER_MS, MONO_CPU_DECISION_EXTRA_MS,
        CHAOS_CPU_THINK_MS, CHAOS_CPU_THINK_JITTER_MS, CHAOS_CPU_DECISION_EXTRA_MS, MONO_PENALTY_CARDS, CHAOS_PENALTY_CARDS,
        CHAOS_KNOCKOUT_CARDS, MONO_BOT_CARD_COST, MONO_BOT_SHED_PCT, MONO_BOT_ATTACK_VALUE, MONO_BOT_HOLD_VALUE, MONO_BOT_COLOUR_VALUE, MONO_BOT_KNOCKOUT_COST, MONO_BOT_CATCH_REACTION_MS,
        RL_TURN_SECONDS, RL_CPU_THINK_MS, RL_CPU_THINK_JITTER_MS, RL_CPU_DECISION_EXTRA_MS, RL_SIX_BOOST_PER_MISS_PCT, RL_SIX_CHANCE_CAP_PCT,
        RL_BOT_BASE_COST, RL_BOT_CAPTURE_WEIGHT_PCT, RL_BOT_EXTRA_TURN, RL_BOT_FINISH, RL_BOT_LEAVE_BASE, RL_BOT_RISK_WEIGHT_PCT, RL_BOT_CHASE, RL_BOT_FLEX, RL_BOT_ENDGAME_CAPTURE_PCT,
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
