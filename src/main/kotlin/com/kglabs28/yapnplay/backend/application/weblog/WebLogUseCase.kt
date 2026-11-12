package com.kglabs28.yapnplay.backend.application.weblog

import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.weblog.WebLog
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogQuery
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogStore
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Web browsers' reports of loading and running the web app: kept (cut to size, unknown kinds as
 * OTHER) for the admin dashboard, and errors also written to the server log. An event keeps the
 * page's own id, so the page's later REPEATS only raise its count (the same error again in that
 * page load is no new event). Reports older than [WebLogPeriod.MAX_DAYS] days are dropped when the
 * dashboard reads them. An admin clears the logs (audited).
 */
class WebLogUseCase(
    private val store: WebLogStore,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AdminAuditRepository,
    private val mapper: WebLogViewMapper = WebLogViewMapper(WebStackDecoder { null }),
) {
    suspend fun record(body: WebLogBody) {
        val kind = body.kind?.uppercase()
        if (kind == REPEATS) {
            body.eventId?.let { id -> store.countOccurrences(id, (body.occurrences ?: 1).coerceIn(1, MAX_OCCURRENCES)) }
            return
        }
        val log = WebLog(
            id = body.eventId?.takeIf { it.isUuid() } ?: ids.newId(),
            createdAt = clock.nowMillis(),
            kind = kind?.takeIf { it in KINDS } ?: OTHER,
            stage = body.stage.cut(STAGE_MAX),
            message = body.message.cut(MESSAGE_MAX),
            userAgent = body.userAgent.cut(AGENT_MAX),
            device = body.device.cut(DEVICE_MAX),
            elapsedMs = (body.elapsedMs ?: 0L).coerceIn(0L, MAX_ELAPSED_MS),
            version = body.version.cut(VERSION_MAX),
            stack = body.stack.cutLines(STACK_MAX),
            installId = body.install.cut(ID_MAX),
            pageLoad = body.pageLoad.cut(ID_MAX),
            occurrences = (body.occurrences ?: 1).coerceIn(1, MAX_OCCURRENCES),
            context = body.context.orEmpty().entries.take(CONTEXT_KEYS).associate { (k, v) -> k.cut(KEY_MAX) to v.cut(VALUE_MAX) },
            breadcrumbs = body.breadcrumbs.orEmpty().takeLast(BREADCRUMBS).map { it.cut(VALUE_MAX) },
        )
        if (store.add(log) && log.kind in WebIssueGrouping.ISSUE_KINDS) {
            LOG.warn("Web ${log.kind} at ${log.stage}: ${log.message} (${log.userAgent})")
        }
    }

    /** The reports of the last [days] days, newest first: of [kind] (null: all) and [version] (null: any). */
    suspend fun recent(kind: String?, days: Int, version: String?): List<WebLogView> {
        val now = clock.nowMillis()
        store.deleteOlderThan(WebLogPeriod.since(now, WebLogPeriod.MAX_DAYS))
        val only = kind?.uppercase()?.takeIf { it in KINDS || it == OTHER }
        val query = WebLogQuery(WebLogPeriod.since(now, days), setOfNotNull(only), version?.takeIf { it.isNotBlank() })
        return store.recent(LIST_LIMIT, query).map { mapper.view(it) }
    }

    /** Clears every report, or only those of [kind]. */
    suspend fun clear(kind: String?, by: String): WebLogsRemoved {
        val only = kind?.uppercase()?.takeIf { it in KINDS || it == OTHER }
        val removed = store.delete(only)
        audit.record(AdminAuditEntry(CLEAR_ACTION, only ?: ALL, null, "$removed removed", by, null, clock.nowMillis()))
        return WebLogsRemoved(removed)
    }

    private fun String?.cut(max: Int): String = orEmpty().filter { !it.isISOControl() }.take(max)

    /** Like [cut], but keeps the line breaks (a stack trace is one frame a line). */
    private fun String?.cutLines(max: Int): String = orEmpty().filter { it == '\n' || !it.isISOControl() }.take(max).trim()

    private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

    companion object {
        private val LOG = LoggerFactory.getLogger(WebLogUseCase::class.java)
        private const val OTHER = "OTHER"
        private const val REPEATS = "REPEATS"
        private val KINDS = WebIssueGrouping.ISSUE_KINDS + setOf("SLOW", "STARTED", "PERF")
        private const val STAGE_MAX = 80
        private const val MESSAGE_MAX = 500
        private const val AGENT_MAX = 300
        private const val DEVICE_MAX = 200
        private const val VERSION_MAX = 40
        private const val STACK_MAX = 6_000
        private const val ID_MAX = 40
        private const val KEY_MAX = 40
        private const val VALUE_MAX = 200
        private const val CONTEXT_KEYS = 30
        private const val BREADCRUMBS = 40
        private const val MAX_OCCURRENCES = 1_000_000
        private const val MAX_ELAPSED_MS = 86_400_000L
        private const val LIST_LIMIT = 300
        private const val CLEAR_ACTION = "WEB_LOG_CLEAR"
        private const val ALL = "ALL"
    }
}
