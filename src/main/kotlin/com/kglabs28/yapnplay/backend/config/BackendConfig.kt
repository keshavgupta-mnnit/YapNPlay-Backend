package com.kglabs28.yapnplay.backend.config

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
)
