package com.kglabs28.yapnplay.backend.config

/**
 * Every number with a meaning, in one place. Values that differ between
 * laptop, tests and the cloud are environment variables instead (see
 * [EnvKeys] and [BackendConfig]); the defaults for those live here too.
 */
object BackendConstants {
    // ── Identity ─────────────────────────────────────────────────────────
    const val DISPLAY_NAME_MAX_LENGTH = 24
    const val GUEST_NAME_PREFIX = "Player-"
    const val GUEST_NAME_NUMBER_MIN = 1000
    const val GUEST_NAME_NUMBER_MAX = 9999
    const val SECRET_BYTES = 32
    const val BEARER_SEPARATOR = '.'

    // ── Profile (contract 0.3.1) ─────────────────────────────────────────
    const val USER_CODE_LENGTH = 6
    const val USER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val USER_CODE_MAX_ATTEMPTS = 20
    const val USERNAME_PATTERN = "[A-Za-z0-9_.]{3,20}"
    const val AVATAR_PREFIX = "a"
    const val AVATAR_DIGITS = 2
    /** Preset avatars a01..a18 (the app ships 18; the contract lists the first 12). */
    const val AVATAR_COUNT = 18
    /** The robot avatars CPU seats use, in turn. */
    val CPU_AVATARS = listOf("a09", "a18")

    // ── Rooms ────────────────────────────────────────────────────────────
    const val ROOM_CODE_LENGTH = 6
    const val ROOM_CODE_ALPHABET = "0123456789"
    const val ROOM_CODE_MAX_ATTEMPTS = 20
    const val ROOM_NAME_MAX_LENGTH = 30
    const val MAX_MEMBERS_PER_ROOM = 10
    const val MIN_HUMANS_TO_START = 2
    const val DEFAULT_ROOM_ICON = "group"
    /** The game server OAuth client from Play Console (public; its secret comes from the environment). */
    const val PLAY_GAMES_CLIENT_ID = "532749629825-lflcpgr3pn64o7emjnvfdld8i0inpqhq.apps.googleusercontent.com"
    /** Room badges the app knows; anything else is stored as the default. */
    val ROOM_ICONS = setOf("group", "dice", "family", "gamepad")

    // ── WebSocket ────────────────────────────────────────────────────────
    const val WS_PATH = "/ws"
    const val WS_PING_SECONDS = 15L
    const val WS_TIMEOUT_SECONDS = 45L
    const val WS_MAX_MESSAGE_BYTES = 8 * 1024
    const val WS_MAX_FRAME_BYTES = 64 * 1024L
    const val WS_MESSAGES_PER_SECOND = 20
    /** Signed-in HTTP calls per minute from one address (the WebSocket has its own limit). */
    const val HTTP_CALLS_PER_MINUTE = 300
    const val SECONDS_PER_MINUTE = 60L
    /** A block can be lifted with an ad at most once in this long (ads are not verified yet). */
    const val AD_CLEAR_COOLDOWN_MS = 24 * 60 * 60 * 1_000L
    const val WS_RECENT_REPLIES = 128
    const val WS_OUTGOING_BUFFER = 256
    const val WS_DEV_TOKEN_QUERY = "token"
    /** Private-use close code (4000–4999) sent to a connection a newer one replaced. */
    const val WS_SESSION_REPLACED_CODE: Short = 4000

    // ── Matches ──────────────────────────────────────────────────────────
    const val MATCH_TICK_MS = 500L
    /** A dropped connection counts as away after this grace (contract). */
    const val DISCONNECT_GRACE_MS = 5_000L
    /** At the start, play waits up to this long for every human to open the table. */
    const val START_WAIT_MS = 20_000L
    /** How long the keep-playing vote at a game's limit stays open. */
    const val EXTENSION_VOTE_MS = 10_000L
    /** Ticks or turn wakes in a row that throw before the match is aborted (ABORTED_ERROR). */
    const val MATCH_FAILURES_BEFORE_ABORT = 5
    const val OPEN_SEAT_AFTER_MS = 30_000L
    const val OPEN_SEAT_AFTER_MISSED_TURNS = 2
    const val SEAT_CLAIM_MS = 30_000L
    const val SPECTATOR_ANSWER_MS = 30_000L
    const val LEADER_CHOICE_MS = 10_000L
    const val IDLE_TURNS_BEFORE_CHECK = 3
    const val ABSENCE_CHECK_MS = 15_000L
    /** A finished match keeps answering snapshot requests for this long. */
    const val FINISHED_MATCH_KEEP_MS = 60_000L
    const val CPU_USER_PREFIX = "cpu-"
    const val CPU_LEVEL = 1
    const val MATCH_OUTGOING_EVENTS_LIMIT = 64

    // ── Moderation (contract) ────────────────────────────────────────────
    const val REPORT_MIN_VOTERS = 2
    const val REPORT_SMALL_GROUP = 4
    const val REPORT_SMALL_GROUP_AGREEMENTS = 2
    const val REPORT_AGREEMENTS = 3
    const val REPORT_VOTE_MS = 15_000L
    const val PENALTY_POINTS = 50
    const val PENALTY_BLOCK_MS = 10 * 60_000L

    // ── Voice ────────────────────────────────────────────────────────────
    /** LiveKit tokens live 10 minutes (contract). */
    const val VOICE_TOKEN_TTL_SECONDS = 600L
    const val VOICE_ROOM_PREFIX = "room-"
    const val VOICE_MATCH_PREFIX = "match-"

    // ── Pushes ───────────────────────────────────────────────────────────
    /** One room-activity push per user and room in this window. */
    const val ROOM_PUSH_THROTTLE_MS = 5 * 60_000L

    // ── Progression (placeholder formula until it is designed) ───────────
    /** XP in hundreds: for playing a match to the end, plus a place bonus (ProgressionPolicy). */
    const val XP_FOR_PLAYING = 100
    /** Every 1,000 XP is a new level. */
    const val XP_PER_LEVEL = 1_000

    // ── Coins (play money: never sold, never cashed out) ─────────────────
    const val DAILY_BONUS_COINS = 100
    const val AD_REWARD_COINS = 500
    const val AD_REWARDS_PER_DAY = 5
    /** Quick Play's default league (an app that does not pick one). */
    const val QUICK_PLAY_ENTRY_COINS = 100
    /** Quick Play leagues: name to entry fee. Players only meet others in the same league; Starter is free. */
    val LEAGUES = listOf("Starter" to 0, "Bronze" to 100, "Silver" to 500, "Gold" to 1_000, "Diamond" to 5_000)
    /** Group games: each player pays this when the match starts; players short of coins play for free but win nothing. */
    const val ROOM_ENTRY_COINS = 100

    // ── Time ─────────────────────────────────────────────────────────────
    const val MILLIS_PER_SECOND = 1_000L
    const val SECONDS_PER_HOUR = 3_600L

    // ── Defaults for environment variables ───────────────────────────────
    const val DEFAULT_PORT = 8080
    const val DEFAULT_MAX_ROOMS_PER_USER = 5
    const val DEFAULT_COUNTDOWN_SECONDS = 10
    const val DEFAULT_GUEST_CREATIONS_PER_HOUR = 20
    const val DEFAULT_EMBEDDED_DB_DIR = "data/postgres"
    const val DEFAULT_EMBEDDED_DB_PORT = 54329
    const val DEFAULT_DB_POOL_SIZE = 5
}
