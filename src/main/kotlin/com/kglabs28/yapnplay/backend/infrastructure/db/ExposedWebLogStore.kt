package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.weblog.WebLog
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.WebLogsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class ExposedWebLogStore(private val db: DbQuery) : WebLogStore {
    override suspend fun add(log: WebLog) {
        db.run {
            WebLogsTable.insert {
                it[id] = UUID.fromString(log.id)
                it[createdAt] = log.createdAt
                it[kind] = log.kind
                it[stage] = log.stage
                it[message] = log.message
                it[userAgent] = log.userAgent
                it[device] = log.device
                it[elapsedMs] = log.elapsedMs
                it[version] = log.version
            }
        }
    }

    override suspend fun recent(limit: Int, kind: String?): List<WebLog> = db.run {
        val query = if (kind == null) WebLogsTable.selectAll() else WebLogsTable.selectAll().where { WebLogsTable.kind eq kind }
        query.orderBy(WebLogsTable.createdAt, SortOrder.DESC).limit(limit).map {
            WebLog(
                id = it[WebLogsTable.id].toString(),
                createdAt = it[WebLogsTable.createdAt],
                kind = it[WebLogsTable.kind],
                stage = it[WebLogsTable.stage],
                message = it[WebLogsTable.message],
                userAgent = it[WebLogsTable.userAgent],
                device = it[WebLogsTable.device],
                elapsedMs = it[WebLogsTable.elapsedMs],
                version = it[WebLogsTable.version],
            )
        }
    }

    override suspend fun deleteOlderThan(before: Long) {
        db.run { WebLogsTable.deleteWhere { createdAt less before } }
    }
}
