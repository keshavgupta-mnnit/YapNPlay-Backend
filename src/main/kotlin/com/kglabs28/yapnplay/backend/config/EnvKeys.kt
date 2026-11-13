package com.kglabs28.yapnplay.backend.config

/** Names of the environment variables the server reads (see README). */
object EnvKeys {
    /** Render and most hosts set PORT; SERVER_PORT wins when both exist. */
    const val SERVER_PORT = "SERVER_PORT"
    const val PORT = "PORT"
    const val DEV_MODE = "DEV_MODE"
    const val DB_URL = "DB_URL"
    const val DB_USER = "DB_USER"
    const val DB_PASSWORD = "DB_PASSWORD"
    const val DB_POOL_SIZE = "DB_POOL_SIZE"
    const val EMBEDDED_DB_DIR = "EMBEDDED_DB_DIR"
    const val EMBEDDED_DB_PORT = "EMBEDDED_DB_PORT"
    const val MAX_ROOMS_PER_USER = "MAX_ROOMS_PER_USER"
    const val COUNTDOWN_SECONDS = "COUNTDOWN_SECONDS"
    const val GUEST_CREATIONS_PER_HOUR = "GUEST_CREATIONS_PER_HOUR"
    /** Comma-separated game ids switched off (the app's `game_enabled_<id>` flags). */
    const val DISABLED_GAMES = "DISABLED_GAMES"
    /** Voice TURN relay, Cloudflare: the TURN key's id and its API token (both secret-ish; from the dashboard). */
    const val CLOUDFLARE_TURN_KEY_ID = "CLOUDFLARE_TURN_KEY_ID"
    const val CLOUDFLARE_TURN_API_TOKEN = "CLOUDFLARE_TURN_API_TOKEN"
    /**
     * Voice TURN relays, fixed servers: TURN_URLS (comma-separated turn:/turns: URLs), TURN_USERNAME,
     * TURN_CREDENTIAL; a second one as TURN2_URLS / TURN2_USERNAME / TURN2_CREDENTIAL, and so on up to TURN3_.
     */
    val TURN_PREFIXES = listOf("TURN_", "TURN2_", "TURN3_")
    const val TURN_URLS_SUFFIX = "URLS"
    const val TURN_USERNAME_SUFFIX = "USERNAME"
    const val TURN_CREDENTIAL_SUFFIX = "CREDENTIAL"
    /** The Firebase service-account JSON (its whole content). Pushes are only logged when unset. */
    const val FIREBASE_SERVICE_ACCOUNT_JSON = "FIREBASE_SERVICE_ACCOUNT_JSON"
    /** Secret for the read-only /admin dashboard. The dashboard is closed when unset. */
    const val ADMIN_TOKEN = "ADMIN_TOKEN"
    const val SENTRY_DSN = "SENTRY_DSN"
    /** Play Games server sign-in: the game server (web) OAuth client. The secret is a real secret. */
    const val PLAY_GAMES_CLIENT_ID = "PLAY_GAMES_CLIENT_ID"
    const val PLAY_GAMES_CLIENT_SECRET = "PLAY_GAMES_CLIENT_SECRET"
    /** The web app's Google sign-in OAuth client id (public). Google sign-in is off when unset. */
    const val GOOGLE_WEB_CLIENT_ID = "GOOGLE_WEB_CLIENT_ID"
    /** Comma-separated origins the web app is served from (CORS). Browsers are refused when unset. */
    const val WEB_APP_ORIGINS = "WEB_APP_ORIGINS"
    /** City Bazaar rounds before the richest player wins (when the room keeps the round limit on). */
    const val CITY_BAZAAR_ROUND_LIMIT = "CITY_BAZAAR_ROUND_LIMIT"
    /** Rounds added when City Bazaar players vote to keep going. */
    const val CITY_BAZAAR_EXTENSION_ROUNDS = "CITY_BAZAAR_EXTENSION_ROUNDS"
    /** The City Bazaar bank's starting reserve (₹). */
    const val CITY_BAZAAR_BANK_START = "CITY_BAZAAR_BANK_START"
}
