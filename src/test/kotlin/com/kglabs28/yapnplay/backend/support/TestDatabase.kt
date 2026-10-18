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

    private const val USER = "postgres"
    private const val DATABASE = "postgres"
    private const val POOL_SIZE = 5
    private const val TRUNCATE = "TRUNCATE play_room_members, play_rooms, users CASCADE"
}
