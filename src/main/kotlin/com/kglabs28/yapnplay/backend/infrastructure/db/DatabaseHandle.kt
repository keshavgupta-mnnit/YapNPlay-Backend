package com.kglabs28.yapnplay.backend.infrastructure.db

import org.jetbrains.exposed.sql.Database
import java.io.Closeable

/** An open, migrated database and what must be closed when the server stops. */
class DatabaseHandle(
    val database: Database,
    private val closeables: List<Closeable>,
) : Closeable {

    override fun close() {
        closeables.forEach { it.close() }
    }
}
