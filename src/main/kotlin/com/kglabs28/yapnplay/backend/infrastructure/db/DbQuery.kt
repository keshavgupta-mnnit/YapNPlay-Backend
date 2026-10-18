package com.kglabs28.yapnplay.backend.infrastructure.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/** Runs database work in a transaction on the IO threads, never on the request threads. */
class DbQuery(private val database: Database) {

    suspend fun <T> run(block: Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
}
