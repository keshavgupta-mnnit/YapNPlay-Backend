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
    /** LiveKit server URL (wss://...). Voice is off when the three LiveKit values are not all set. */
    const val LIVEKIT_URL = "LIVEKIT_URL"
    const val LIVEKIT_API_KEY = "LIVEKIT_API_KEY"
    const val LIVEKIT_API_SECRET = "LIVEKIT_API_SECRET"
    /** The Firebase service-account JSON (its whole content). Pushes are only logged when unset. */
    const val FIREBASE_SERVICE_ACCOUNT_JSON = "FIREBASE_SERVICE_ACCOUNT_JSON"
    /** Secret for the read-only /admin dashboard. The dashboard is closed when unset. */
    const val ADMIN_TOKEN = "ADMIN_TOKEN"
    const val SENTRY_DSN = "SENTRY_DSN"
    /** Play Games server sign-in: the game server (web) OAuth client. The secret is a real secret. */
    const val PLAY_GAMES_CLIENT_ID = "PLAY_GAMES_CLIENT_ID"
    const val PLAY_GAMES_CLIENT_SECRET = "PLAY_GAMES_CLIENT_SECRET"
    /** City Bazaar rounds before the richest player wins (when the room keeps the round limit on). */
    const val CITY_BAZAAR_ROUND_LIMIT = "CITY_BAZAAR_ROUND_LIMIT"
    /** Rounds added when City Bazaar players vote to keep going. */
    const val CITY_BAZAAR_EXTENSION_ROUNDS = "CITY_BAZAAR_EXTENSION_ROUNDS"
    /** The City Bazaar bank's starting reserve (₹). */
    const val CITY_BAZAAR_BANK_START = "CITY_BAZAAR_BANK_START"
}
