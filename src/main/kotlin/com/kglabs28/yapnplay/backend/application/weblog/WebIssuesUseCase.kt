package com.kglabs28.yapnplay.backend.application.weblog

import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.weblog.WebIssueState
import com.kglabs28.yapnplay.backend.domain.weblog.WebIssueStore
import com.kglabs28.yapnplay.backend.domain.weblog.WebLog
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogQuery
import com.kglabs28.yapnplay.backend.domain.weblog.WebLogStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The web app's crash reporting as Crashlytics shows it: errors grouped into issues
 * ([WebIssueGrouping]) with their events, occurrences, users, versions and a daily trend; crash-free
 * sessions and users; one issue's events with how they split by OS, browser, device, screen and
 * version. All for the last days ([WebLogPeriod]) and one version or all. Closing an issue keeps its
 * events; it shows as regressed when it happens again after that.
 */
class WebIssuesUseCase(
    private val store: WebLogStore,
    private val issueStates: WebIssueStore,
    private val clock: Clock,
    private val audit: AdminAuditRepository,
    private val mapper: WebLogViewMapper,
) {
    suspend fun page(days: Int, version: String?): WebIssuesPage {
        val now = clock.nowMillis()
        val since = WebLogPeriod.since(now, days)
        val all = store.recent(SCAN_LIMIT, WebLogQuery(since, version = version.orNull()))
        val closed = issueStates.closed()
        val issues = all.filter { it.kind in WebIssueGrouping.ISSUE_KINDS }
            .groupBy { WebIssueGrouping.fingerprint(it.kind, it.message) }
            .map { (id, events) -> issue(id, events, closed[id], days, now) }
            .sortedWith(compareByDescending<WebIssueView> { it.state != CLOSED }.thenByDescending { it.events }.thenByDescending { it.lastSeen })
        return WebIssuesPage(summary(all, days, now), store.versions(since), issues)
    }

    suspend fun detail(id: String, days: Int, version: String?): WebIssueDetail? {
        val now = clock.nowMillis()
        val events = store.recent(SCAN_LIMIT, WebLogQuery(WebLogPeriod.since(now, days), WebIssueGrouping.ISSUE_KINDS, version.orNull()))
            .filter { WebIssueGrouping.fingerprint(it.kind, it.message) == id }
        if (events.isEmpty()) return null
        val issue = issue(id, events, issueStates.closed()[id], days, now)
        val breakdowns = listOf(
            breakdown("OS", events) { WebAgent.os(it.userAgent) },
            breakdown("Browser", events) { WebAgent.browser(it.userAgent) },
            breakdown("Device", events) { it.device.split(", ").firstOrNull { part -> part.startsWith("screen") } ?: it.device },
            breakdown("Screen", events) { it.context["screen"] ?: it.stage },
            breakdown("Version", events) { it.version.ifEmpty { "unknown" } },
            breakdown("Orientation", events) { it.context["orientation"] ?: "unknown" },
        )
        return WebIssueDetail(issue, breakdowns, events.take(EVENTS_SHOWN).map { mapper.view(it) })
    }

    suspend fun close(id: String, note: String, by: String) {
        val now = clock.nowMillis()
        issueStates.close(WebIssueState(id, now, by.take(BY_MAX), note.take(NOTE_MAX)))
        audit.record(AdminAuditEntry(CLOSE_ACTION, id, null, note.take(NOTE_MAX), by, null, now))
    }

    suspend fun reopen(id: String, by: String) {
        issueStates.reopen(id)
        audit.record(AdminAuditEntry(REOPEN_ACTION, id, null, null, by, null, clock.nowMillis()))
    }

    private suspend fun issue(id: String, events: List<WebLog>, closed: WebIssueState?, days: Int, now: Long): WebIssueView {
        val latest = events.maxBy { it.createdAt }
        val lastSeen = latest.createdAt
        val state = when {
            closed == null -> OPEN
            lastSeen > closed.closedAt -> REGRESSED
            else -> CLOSED
        }
        val where = where(events)
        return WebIssueView(
            id = id,
            kind = latest.kind,
            title = WebIssueGrouping.title(latest.message),
            where = where,
            events = events.size,
            occurrences = events.sumOf { it.occurrences },
            users = events.map { it.user() }.toSet().size,
            firstSeen = events.minOf { it.createdAt },
            lastSeen = lastSeen,
            versions = events.sortedByDescending { it.createdAt }.map { it.version }.filter { it.isNotEmpty() }.distinct(),
            days = perDay(events, days, now),
            state = state,
            closedAt = closed?.closedAt ?: 0L,
            closedBy = closed?.closedBy.orEmpty(),
            note = closed?.note.orEmpty(),
        )
    }

    /** Where it broke, from the first of its newest stacks that its build's map decodes (empty without one). */
    private suspend fun where(events: List<WebLog>): String {
        for (event in events.filter { it.stack.isNotEmpty() }.sortedByDescending { it.createdAt }.take(WHERE_TRIES)) {
            WebIssueGrouping.where(mapper.view(event).decodedStack).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    /** Sessions are page loads that reported anything (a start at least); crashing ones had a CRASH or FAILED. */
    private fun summary(all: List<WebLog>, days: Int, now: Long): WebCrashSummary {
        val withSession = all.filter { it.pageLoad.isNotEmpty() }
        val crashes = withSession.filter { it.kind in FATAL }
        val sessions = withSession.map { it.pageLoad }.toSet()
        val crashSessions = crashes.map { it.pageLoad }.toSet()
        val users = withSession.map { it.user() }.toSet()
        val crashUsers = crashes.map { it.user() }.toSet()
        return WebCrashSummary(
            sessions = sessions.size,
            crashSessions = crashSessions.size,
            users = users.size,
            crashUsers = crashUsers.size,
            crashFreeSessionsPct = freePct(sessions.size, crashSessions.size),
            crashFreeUsersPct = freePct(users.size, crashUsers.size),
            days = perDay(crashes.distinctBy { it.pageLoad }, days, now),
        )
    }

    private fun breakdown(name: String, events: List<WebLog>, value: (WebLog) -> String): WebBreakdown {
        val counts = events.groupingBy(value).eachCount().entries.sortedByDescending { it.value }
        return WebBreakdown(name, counts.map { (v, n) -> WebShare(v.ifEmpty { "unknown" }, n, pct(n, events.size)) })
    }

    /** Events a day over the period, oldest first (India time, as the rest of the dashboard). */
    private fun perDay(events: List<WebLog>, days: Int, now: Long): List<WebDayCount> {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZONE)
        val counts = events.groupingBy { LocalDate.ofInstant(Instant.ofEpochMilli(it.createdAt), ZONE) }.eachCount()
        return (days - 1 downTo 0).map { back -> today.minusDays(back.toLong()).let { WebDayCount(it.toString(), counts[it] ?: 0) } }
    }

    /** A browser (its random id), or the page load / event for reports from before ids were sent. */
    private fun WebLog.user(): String = installId.ifEmpty { pageLoad.ifEmpty { id } }

    private fun freePct(total: Int, bad: Int): Double = if (total == 0) 100.0 else pct(total - bad, total)

    private fun pct(part: Int, total: Int): Double = if (total == 0) 0.0 else Math.round(part * 1000.0 / total) / 10.0

    private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        const val OPEN = "OPEN"
        const val CLOSED = "CLOSED"
        const val REGRESSED = "REGRESSED"
        val FATAL = setOf("CRASH", "FAILED")
        const val SCAN_LIMIT = 5_000
        const val EVENTS_SHOWN = 100
        /** How many of an issue's newest stacks are tried for where it broke. */
        const val WHERE_TRIES = 5
        const val BY_MAX = 80
        const val NOTE_MAX = 500
        const val CLOSE_ACTION = "WEB_ISSUE_CLOSE"
        const val REOPEN_ACTION = "WEB_ISSUE_REOPEN"
        val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
    }
}
