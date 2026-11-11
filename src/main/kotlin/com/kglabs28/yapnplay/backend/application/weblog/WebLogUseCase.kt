package com.kglabs28.yapnplay.backend.application.weblog

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
)

/**
 * Web browsers' reports of loading and running the web app: kept (cut to size, unknown kinds as
 * OTHER) for the admin dashboard, and failures also written to the server log. Reports older than
 * [KEEP_DAYS] days are dropped when the dashboard reads them.
 */
class WebLogUseCase(
    private val store: WebLogStore,
    private val ids: IdGenerator,
    private val clock: Clock,
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
        )
        store.add(log)
        if (kind == FAILED || kind == CRASH) log.warn("Web $kind at ${log.stage}: ${log.message} (${log.userAgent})")
    }

    suspend fun recent(kind: String?): List<WebLogView> {
        store.deleteOlderThan(clock.nowMillis() - KEEP_DAYS * MILLIS_PER_DAY)
        return store.recent(LIST_LIMIT, kind?.uppercase()?.takeIf { it in KINDS || it == OTHER }).map {
            WebLogView(it.createdAt, it.kind, it.stage, it.message, it.userAgent, it.device, it.elapsedMs, it.version)
        }
    }

    private fun String?.cut(max: Int): String = orEmpty().filter { !it.isISOControl() }.take(max)

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
        private const val MAX_ELAPSED_MS = 3_600_000L
        private const val LIST_LIMIT = 200
        private const val KEEP_DAYS = 14L
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
