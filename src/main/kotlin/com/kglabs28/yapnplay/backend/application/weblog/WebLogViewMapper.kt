package com.kglabs28.yapnplay.backend.application.weblog

import com.kglabs28.yapnplay.backend.domain.weblog.WebLog

/** A stored report as the dashboard shows it: the stack decoded when its build's map is there, OS and browser read. */
class WebLogViewMapper(private val decoder: WebStackDecoder) {
    suspend fun view(log: WebLog): WebLogView {
        val decoded = decoder.decode(log.stack, log.version)
        return WebLogView(
            id = log.id, createdAt = log.createdAt, kind = log.kind, stage = log.stage, message = log.message,
            userAgent = log.userAgent, device = log.device, elapsedMs = log.elapsedMs, version = log.version,
            stack = log.stack, decodedStack = decoded.stack, decodeNote = decoded.note,
            installId = log.installId, pageLoad = log.pageLoad, occurrences = log.occurrences,
            context = log.context, breadcrumbs = log.breadcrumbs,
            os = WebAgent.os(log.userAgent), browser = WebAgent.browser(log.userAgent),
        )
    }
}

/** A dashboard period: the last [days] days (1 to [MAX_DAYS], which is how long reports are kept). */
object WebLogPeriod {
    const val MAX_DAYS = 14
    const val DEFAULT_DAYS = 7
    const val MILLIS_PER_DAY = 86_400_000L

    fun days(param: String?): Int = param?.toIntOrNull()?.coerceIn(1, MAX_DAYS) ?: DEFAULT_DAYS

    fun since(now: Long, days: Int): Long = now - days * MILLIS_PER_DAY
}
