package com.kglabs28.yapnplay.backend.config

/**
 * Reads [BackendConfig] from environment variables, with safe defaults.
 * Without DB_URL the server only starts in DEV_MODE (embedded database);
 * in the cloud a missing DB_URL is a startup error, never a silent fallback.
 */
class BackendConfigLoader(private val env: (String) -> String? = System::getenv) {

    fun load(): BackendConfig {
        val devMode = env(EnvKeys.DEV_MODE).toBoolean()
        return BackendConfig(
            port = int(EnvKeys.SERVER_PORT) ?: int(EnvKeys.PORT) ?: BackendConstants.DEFAULT_PORT,
            devMode = devMode,
            database = database(devMode),
            maxRoomsPerUser = int(EnvKeys.MAX_ROOMS_PER_USER) ?: BackendConstants.DEFAULT_MAX_ROOMS_PER_USER,
            countdownSeconds = int(EnvKeys.COUNTDOWN_SECONDS) ?: BackendConstants.DEFAULT_COUNTDOWN_SECONDS,
            guestCreationsPerHour = int(EnvKeys.GUEST_CREATIONS_PER_HOUR)
                ?: BackendConstants.DEFAULT_GUEST_CREATIONS_PER_HOUR,
            disabledGames = env(EnvKeys.DISABLED_GAMES).orEmpty()
                .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            voice = voice(),
            firebaseServiceAccountJson = env(EnvKeys.FIREBASE_SERVICE_ACCOUNT_JSON)?.takeIf { it.isNotBlank() },
            adminToken = env(EnvKeys.ADMIN_TOKEN)?.trim()?.takeIf { it.isNotEmpty() },
            sentryDsn = env(EnvKeys.SENTRY_DSN)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun database(devMode: Boolean): DatabaseConfig {
        val url = env(EnvKeys.DB_URL)
        if (url.isNullOrBlank()) {
            check(devMode) { BackendStrings.MISSING_DB_URL }
            return DatabaseConfig.Embedded(
                dataDir = env(EnvKeys.EMBEDDED_DB_DIR) ?: BackendConstants.DEFAULT_EMBEDDED_DB_DIR,
                port = int(EnvKeys.EMBEDDED_DB_PORT) ?: BackendConstants.DEFAULT_EMBEDDED_DB_PORT,
            )
        }
        return DatabaseConfig.External(
            jdbcUrl = url,
            user = env(EnvKeys.DB_USER).orEmpty(),
            password = env(EnvKeys.DB_PASSWORD).orEmpty(),
            poolSize = int(EnvKeys.DB_POOL_SIZE) ?: BackendConstants.DEFAULT_DB_POOL_SIZE,
        )
    }

    private fun voice(): VoiceConfig? {
        val url = env(EnvKeys.LIVEKIT_URL)?.takeIf { it.isNotBlank() } ?: return null
        val key = env(EnvKeys.LIVEKIT_API_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val secret = env(EnvKeys.LIVEKIT_API_SECRET)?.takeIf { it.isNotBlank() } ?: return null
        return VoiceConfig(url, key, secret)
    }

    private fun int(key: String): Int? = env(key)?.trim()?.toIntOrNull()
}
