package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.config.DatabaseConfig
import com.kglabs28.yapnplay.backend.config.BackendConfig

/** Server settings for tests: a 1-second countdown so tests stay fast. */
fun testConfig(
    countdownSeconds: Int = 1,
    maxRoomsPerUser: Int = 5,
    guestCreationsPerHour: Int = 1_000,
    disabledGames: Set<String> = emptySet(),
) = BackendConfig(
    port = 0,
    devMode = true,
    database = DatabaseConfig.Embedded(dataDir = "unused", port = 0),
    maxRoomsPerUser = maxRoomsPerUser,
    countdownSeconds = countdownSeconds,
    guestCreationsPerHour = guestCreationsPerHour,
    disabledGames = disabledGames,
)
