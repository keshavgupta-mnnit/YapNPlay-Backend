package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.weblog.WebSymbolStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.WebSymbolMapsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class ExposedWebSymbolStore(private val db: DbQuery) : WebSymbolStore {
    override suspend fun put(version: String, uploadedAt: Long, gzipped: ByteArray) {
        db.run {
            WebSymbolMapsTable.upsert {
                it[WebSymbolMapsTable.version] = version
                it[WebSymbolMapsTable.uploadedAt] = uploadedAt
                it[data] = gzipped
            }
        }
    }

    override suspend fun get(version: String): ByteArray? = db.run {
        WebSymbolMapsTable.select(WebSymbolMapsTable.data).where { WebSymbolMapsTable.version eq version }
            .firstOrNull()?.get(WebSymbolMapsTable.data)
    }

    override suspend fun keepNewest(count: Int) {
        db.run {
            val old = WebSymbolMapsTable.select(WebSymbolMapsTable.version)
                .orderBy(WebSymbolMapsTable.uploadedAt, SortOrder.DESC)
                .drop(count)
                .map { it[WebSymbolMapsTable.version] }
            if (old.isNotEmpty()) WebSymbolMapsTable.deleteWhere { version inList old }
        }
    }
}
