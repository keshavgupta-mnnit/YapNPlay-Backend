package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.weblog.WebLog
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogQuery
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogStore
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.WebLogsTable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class ExposedWebLogStore(private val db: DbQuery) : WebLogStore {
    override suspend fun add(log: WebLog): Boolean = db.run {
        WebLogsTable.insertIgnore {
            it[id] = UUID.fromString(log.id)
            it[createdAt] = log.createdAt
            it[kind] = log.kind
            it[stage] = log.stage
            it[message] = log.message
            it[userAgent] = log.userAgent
            it[device] = log.device
            it[elapsedMs] = log.elapsedMs
            it[version] = log.version
            it[stack] = log.stack
            it[installId] = log.installId
            it[pageLoad] = log.pageLoad
            it[occurrences] = log.occurrences
            it[context] = if (log.context.isEmpty()) "" else JSON.encodeToString(CONTEXT, log.context)
            it[breadcrumbs] = log.breadcrumbs.joinToString("\n")
        }.insertedCount > 0
    }

    override suspend fun recent(limit: Int, query: WebLogQuery): List<WebLog> = db.run {
        WebLogsTable.selectAll()
            .where {
                var where: Op<Boolean> = WebLogsTable.createdAt greaterEq query.since
                if (query.kinds.isNotEmpty()) where = where and (WebLogsTable.kind inList query.kinds)
                query.version?.let { where = where and (WebLogsTable.version eq it) }
                where
            }
            .orderBy(WebLogsTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it.toWebLog() }
    }

    override suspend fun countOccurrences(id: String, occurrences: Int) {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return
        db.run {
            WebLogsTable.update({ (WebLogsTable.id eq uuid) and (WebLogsTable.occurrences less occurrences) }) {
                it[WebLogsTable.occurrences] = occurrences
            }
        }
    }

    override suspend fun versions(since: Long): List<String> = db.run {
        val latest = WebLogsTable.createdAt.max()
        WebLogsTable.select(WebLogsTable.version, latest)
            .where { WebLogsTable.createdAt greaterEq since }
            .groupBy(WebLogsTable.version)
            .orderBy(latest, SortOrder.DESC)
            .map { it[WebLogsTable.version] }
            .filter { it.isNotEmpty() }
    }

    override suspend fun delete(kind: String?): Int = db.run {
        WebLogsTable.deleteWhere { if (kind == null) Op.TRUE else WebLogsTable.kind eq kind }
    }

    override suspend fun deleteOlderThan(before: Long) {
        db.run { WebLogsTable.deleteWhere { createdAt less before } }
    }

    private fun ResultRow.toWebLog() = WebLog(
        id = this[WebLogsTable.id].toString(),
        createdAt = this[WebLogsTable.createdAt],
        kind = this[WebLogsTable.kind],
        stage = this[WebLogsTable.stage],
        message = this[WebLogsTable.message],
        userAgent = this[WebLogsTable.userAgent],
        device = this[WebLogsTable.device],
        elapsedMs = this[WebLogsTable.elapsedMs],
        version = this[WebLogsTable.version],
        stack = this[WebLogsTable.stack],
        installId = this[WebLogsTable.installId],
        pageLoad = this[WebLogsTable.pageLoad],
        occurrences = this[WebLogsTable.occurrences],
        context = this[WebLogsTable.context].takeIf { it.isNotEmpty() }
            ?.let { runCatching { JSON.decodeFromString(CONTEXT, it) }.getOrNull() }.orEmpty(),
        breadcrumbs = this[WebLogsTable.breadcrumbs].takeIf { it.isNotEmpty() }?.lines().orEmpty(),
    )

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val CONTEXT = MapSerializer(String.serializer(), String.serializer())
    }
}
