package com.kglabs28.yapnplay.backend.config

/** Where the database is. */
sealed interface DatabaseConfig {

    /** A real PostgreSQL server (Neon, Oracle, Docker...). */
    data class External(
        val jdbcUrl: String,
        val user: String,
        val password: String,
        val poolSize: Int,
    ) : DatabaseConfig

    /**
     * A PostgreSQL the server starts itself, stored in [dataDir]. Only for
     * running on the laptop; data survives restarts.
     */
    data class Embedded(
        val dataDir: String,
        val port: Int,
    ) : DatabaseConfig
}
