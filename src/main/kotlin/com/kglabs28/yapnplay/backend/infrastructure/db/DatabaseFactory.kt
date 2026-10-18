package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens the database: a connection pool (Hikari), then the migrations
 * (Flyway applies every V*.sql not applied yet, in order), then Exposed.
 */
class DatabaseFactory {

    fun open(config: DatabaseConfig): DatabaseHandle = when (config) {
        is DatabaseConfig.External -> connect(config.jdbcUrl, config.user, config.password, config.poolSize)
        is DatabaseConfig.Embedded -> openEmbedded(config)
    }

    /** Connects to a database that is already running. */
    fun connect(jdbcUrl: String, user: String, password: String, poolSize: Int, extra: List<Closeable> = emptyList()): DatabaseHandle {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                username = user
                this.password = password
                maximumPoolSize = poolSize
            },
        )
        Flyway.configure().dataSource(dataSource).load().migrate()
        return DatabaseHandle(Database.connect(dataSource), listOf(dataSource) + extra)
    }

    private fun openEmbedded(config: DatabaseConfig.Embedded): DatabaseHandle {
        val dir = Path.of(config.dataDir).toAbsolutePath()
        Files.createDirectories(dir)
        val postgres = EmbeddedPostgres.builder()
            .setDataDirectory(dir)
            .setCleanDataDirectory(false)
            .setPort(config.port)
            .start()
        return connect(
            jdbcUrl = postgres.getJdbcUrl(EMBEDDED_USER, EMBEDDED_DATABASE),
            user = EMBEDDED_USER,
            password = "",
            poolSize = EMBEDDED_POOL_SIZE,
            extra = listOf(postgres),
        )
    }

    private companion object {
        const val EMBEDDED_USER = "postgres"
        const val EMBEDDED_DATABASE = "postgres"
        const val EMBEDDED_POOL_SIZE = 5
    }
}
