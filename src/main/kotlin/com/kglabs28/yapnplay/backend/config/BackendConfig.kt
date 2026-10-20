package com.kglabs28.yapnplay.backend.config

import com.kglabs28.yapnplay.backend.games.citybazaar.utils.BANK_START
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.EXTENSION_ROUNDS
import com.kglabs28.yapnplay.backend.games.citybazaar.utils.ROUND_LIMIT

/**
 * Everything the server needs to know about where it runs. Built once
 * from environment variables by [BackendConfigLoader]; tests build their
 * own. Never holds secrets in code: [database] comes from the environment.
 */
data class BackendConfig(
    val port: Int,
    /** Laptop/testing conveniences: browser token in the WebSocket URL, embedded database. */
    val devMode: Boolean,
    val database: DatabaseConfig,
    val maxRoomsPerUser: Int,
    val countdownSeconds: Int,
    val guestCreationsPerHour: Int,
    val disabledGames: Set<String>,
    /** Null when voice is not set up. */
    val voice: VoiceConfig? = null,
    /** Firebase service-account JSON; null: pushes are only logged. */
    val firebaseServiceAccountJson: String? = null,
    /** Token for the read-only /admin dashboard; null: the dashboard refuses everyone. */
    val adminToken: String? = null,
    /** Sentry project DSN for server error tracking; null: errors only go to the log. */
    val sentryDsn: String? = null,
    /** City Bazaar round limit (rooms with the limit on, and Quick Play). */
    val cityBazaarRoundLimit: Int = ROUND_LIMIT,
    /** Rounds added when City Bazaar players vote to keep going. */
    val cityBazaarExtensionRounds: Int = EXTENSION_ROUNDS,
    /** The City Bazaar bank's starting reserve (₹). */
    val cityBazaarBankStart: Int = BANK_START,
    /** Play Games game-server OAuth client id (public). */
    val playGamesClientId: String = BackendConstants.PLAY_GAMES_CLIENT_ID,
    /** Its client secret; null: Play Games sign-in answers PLAY_GAMES_UNAVAILABLE. */
    val playGamesClientSecret: String? = null,
)
