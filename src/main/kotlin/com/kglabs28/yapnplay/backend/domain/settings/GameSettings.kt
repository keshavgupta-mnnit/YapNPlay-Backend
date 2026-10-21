package com.kglabs28.yapnplay.backend.domain.settings

/** Where a setting shows on the admin "Game settings" page. */
enum class SettingGroup(val label: String) {
    ECONOMY("Coins"),
    XP("XP and level"),
    MATCH("All matches"),
    CITY_BAZAAR("City Bazaar"),
    ROYAL_LUDO("Royal Ludo"),
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
    val AD_REWARD = IntSetting("coins.ad_reward", SettingGroup.ECONOMY, "Coins per rewarded ad", 500, 0, MAX_COINS)
    val AD_DAILY_CAP = IntSetting("coins.ad_daily_cap", SettingGroup.ECONOMY, "Rewarded ads per day", 5, 0, 50)
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
    val GROUP_FEE = IntSetting("coins.group_fee", SettingGroup.ECONOMY, "Group game fee per player (coins)", 100, 0, MAX_COINS)
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
    val XP_PER_LEVEL = IntSetting("xp.per_level", SettingGroup.XP, "XP per level", 1_000, 100, 1_000_000)

    // ── Every match ───────────────────────────────────────────────────────
    val VOTE_SECONDS = IntSetting("match.vote_seconds", SettingGroup.MATCH, "Keep-playing vote (seconds)", 10, 5, MAX_SECONDS)
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
    val CB_CPU_THINK_MS = IntSetting("cb.cpu_think_ms", SettingGroup.CITY_BAZAAR, "CPU think time (ms)", 1_000, 0, 10_000)
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

    // ── Royal Ludo ────────────────────────────────────────────────────────
    val RL_TURN_SECONDS = IntSetting("rl.turn_seconds", SettingGroup.ROYAL_LUDO, "Turn time (seconds)", 10, 5, MAX_SECONDS)
    val RL_CPU_THINK_MS = IntSetting("rl.cpu_think_ms", SettingGroup.ROYAL_LUDO, "CPU think time (ms)", 900, 0, 10_000)
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
        DAILY_BONUS, AD_REWARD, AD_DAILY_CAP, LEAGUE_FEES, GROUP_FEE, COMPLETION_COINS, COMPLETION_DAILY_CAP, COMPLETION_MIN_MINUTES,
    ) + POT_SPLITS.values + listOf(
        XP_PER_PLAY, XP_PLACE_BONUS, XP_PER_LEVEL,
        VOTE_SECONDS, START_WAIT_SECONDS, QUICK_PLAY_SEARCH_SECONDS, END_VOTE_SECONDS, END_VOTE_COOLDOWN_MINUTES,
        BOT_STANDING_MIN_PCT, BOT_STANDING_MAX_PCT,
        CB_TURN_SECONDS, CB_ROUND_LIMIT, CB_EXTENSION_ROUNDS, CB_EXTENSION_MINUTES, CB_BANK_START,
        CB_CLUB_REST_EACH, CB_MESSAGE_PAUSE_MS, CB_MESSAGE_PAUSE_CAP_MS, CB_CPU_THINK_MS,
        CB_CPU_RESERVE, CB_CPU_SAFETY_BUFFER, CB_AUTO_BUY_RESERVE, CB_AUTO_SAFETY_BUFFER,
        CB_BOT_RUIN_WEIGHT_PCT, CB_BOT_DENIAL_WEIGHT_PCT, CB_BOT_RUIN_TARGET_PCT, CB_BOT_PRESSURE_VALUE, CB_BOT_COMFORT_PCT, CB_BOT_JAIL_POSTPONE_PCT, CB_BOT_DEFAULT_ROUNDS,
        RL_TURN_SECONDS, RL_CPU_THINK_MS, RL_SIX_BOOST_PER_MISS_PCT, RL_SIX_CHANCE_CAP_PCT,
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
