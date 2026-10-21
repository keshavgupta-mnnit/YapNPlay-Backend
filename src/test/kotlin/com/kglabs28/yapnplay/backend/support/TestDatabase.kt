package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseFactory
import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseHandle
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.DriverManager

/**
 * One real PostgreSQL for the whole test run (started once, in a temp
 * folder), migrated like production. [reset] empties every table.
 */
object TestDatabase {
    private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.start() }
    private val url: String by lazy { postgres.getJdbcUrl(USER, DATABASE) }

    val handle: DatabaseHandle by lazy { DatabaseFactory().connect(url, USER, "", POOL_SIZE) }

    fun reset() {
        handle
        DriverManager.getConnection(url, USER, "").use { connection ->
            connection.createStatement().use { it.execute(TRUNCATE) }
        }
    }

    /** Runs one SQL statement (to simulate what a crash leaves behind). */
    fun execute(sql: String) {
        DriverManager.getConnection(url, USER, "").use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    /** The first column of the first row of [sql], as text; null when there is no row. */
    fun queryText(sql: String): String? =
        DriverManager.getConnection(url, USER, "").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { if (it.next()) it.getString(1) else null }
            }
        }

    private const val USER = "postgres"
    private const val DATABASE = "postgres"
    private const val POOL_SIZE = 5
    private const val TRUNCATE = "TRUNCATE match_outcomes, active_matches, play_room_members, play_rooms, users, game_settings, admin_audit CASCADE"
}
