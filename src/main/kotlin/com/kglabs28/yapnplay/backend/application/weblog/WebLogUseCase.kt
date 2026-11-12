package com.kglabs28.yapnplay.backend.application.weblog

import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.weblog.WebLog
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogStore
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

/** What a browser sends (all optional: a broken page sends what it can). */
@Serializable
data class WebLogBody(
    val kind: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val userAgent: String? = null,
    val device: String? = null,
    val elapsedMs: Long? = null,
    val version: String? = null,
    val stack: String? = null,
)

/** A report as the admin dashboard lists it. */
@Serializable
data class WebLogView(
    val createdAt: Long,
    val kind: String,
    val stage: String,
    val message: String,
    val userAgent: String,
    val device: String,
    val elapsedMs: Long,
    val version: String,
    val stack: String = "",
)

/** Closing one bug: every report of this [kind] with exactly this [message]. */
@Serializable
data class WebLogCloseBody(val kind: String, val message: String)

/** How many reports a close or a clear removed. */
@Serializable
data class WebLogsRemoved(val removed: Int)

/**
 * Web browsers' reports of loading and running the web app: kept (cut to size, unknown kinds as
 * OTHER) for the admin dashboard, and failures also written to the server log. Reports older than
 * [KEEP_DAYS] days are dropped when the dashboard reads them. An admin closes a bug (its reports go;
 * it shows again if it comes back) or clears the logs, both audited.
 */
class WebLogUseCase(
    private val store: WebLogStore,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val audit: AdminAuditRepository,
) {
    suspend fun record(body: WebLogBody) {
        val kind = body.kind?.uppercase()?.takeIf { it in KINDS } ?: OTHER
        val log = WebLog(
            id = ids.newId(),
            createdAt = clock.nowMillis(),
            kind = kind,
            stage = body.stage.cut(STAGE_MAX),
            message = body.message.cut(MESSAGE_MAX),
            userAgent = body.userAgent.cut(AGENT_MAX),
            device = body.device.cut(DEVICE_MAX),
            elapsedMs = (body.elapsedMs ?: 0L).coerceIn(0L, MAX_ELAPSED_MS),
            version = body.version.cut(VERSION_MAX),
            stack = body.stack.cutLines(STACK_MAX),
        )
        store.add(log)
        if (kind == FAILED || kind == CRASH) log.warn("Web $kind at ${log.stage}: ${log.message} (${log.userAgent})")
    }

    suspend fun recent(kind: String?): List<WebLogView> {
        store.deleteOlderThan(clock.nowMillis() - KEEP_DAYS * MILLIS_PER_DAY)
        return store.recent(LIST_LIMIT, kind?.uppercase()?.takeIf { it in KINDS || it == OTHER }).map {
            WebLogView(it.createdAt, it.kind, it.stage, it.message, it.userAgent, it.device, it.elapsedMs, it.version, it.stack)
        }
    }

    suspend fun close(body: WebLogCloseBody, by: String): WebLogsRemoved {
        val kind = body.kind.uppercase()
        val removed = store.delete(kind, body.message)
        audit.record(AdminAuditEntry(CLOSE_ACTION, "$kind: ${body.message}".take(AUDIT_TARGET_MAX), null, "$removed removed", by, null, clock.nowMillis()))
        return WebLogsRemoved(removed)
    }

    /** Clears every report, or only those of [kind]. */
    suspend fun clear(kind: String?, by: String): WebLogsRemoved {
        val only = kind?.uppercase()?.takeIf { it in KINDS || it == OTHER }
        val removed = store.delete(only, null)
        audit.record(AdminAuditEntry(CLEAR_ACTION, only ?: ALL, null, "$removed removed", by, null, clock.nowMillis()))
        return WebLogsRemoved(removed)
    }

    private fun String?.cut(max: Int): String = orEmpty().filter { !it.isISOControl() }.take(max)

    /** Like [cut], but keeps the line breaks (a stack trace is one frame a line). */
    private fun String?.cutLines(max: Int): String = orEmpty().filter { it == '\n' || !it.isISOControl() }.take(max).trim()

    private fun WebLog.warn(text: String) = LOG.warn(text)

    companion object {
        private val LOG = LoggerFactory.getLogger(WebLogUseCase::class.java)
        private const val FAILED = "FAILED"
        private const val CRASH = "CRASH"
        private const val OTHER = "OTHER"
        private val KINDS = setOf(FAILED, "SLOW", "STARTED", CRASH, "PERF")
        private const val STAGE_MAX = 80
        private const val MESSAGE_MAX = 500
        private const val AGENT_MAX = 300
        private const val DEVICE_MAX = 200
        private const val VERSION_MAX = 40
        private const val STACK_MAX = 6_000
        private const val MAX_ELAPSED_MS = 3_600_000L
        private const val LIST_LIMIT = 200
        private const val KEEP_DAYS = 14L
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val CLOSE_ACTION = "WEB_LOG_CLOSE"
        private const val CLEAR_ACTION = "WEB_LOG_CLEAR"
        private const val ALL = "ALL"
        private const val AUDIT_TARGET_MAX = 200
    }
}
