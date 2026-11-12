package com.kglabs28.yapnplay.backend.domain.weblog

/**
 * One report from a web browser: [kind] (FAILED: it broke before the first screen; SLOW: still
 * loading; STARTED: the first screen showed; CRASH: an error inside the running app; NON_FATAL: an
 * error the app caught; PERF: frames a second), how far it got ([stage]), what went wrong
 * ([message]), the browser ([userAgent]), the device ([device]: memory, cores, screen), the time
 * since the page opened ([elapsedMs]), the web build ([version]) and, for an error, its full stack
 * trace ([stack], one frame a line; empty when there is none).
 *
 * Like a Crashlytics event it also knows the browser ([installId], a random id: users affected) and
 * the page load ([pageLoad]: sessions), how many times it happened in that load ([occurrences]), the
 * page's state and the app's keys ([context]: the screen…) and the last things that happened
 * ([breadcrumbs], oldest first).
 */
data class WebLog(
    val id: String,
    val createdAt: Long,
    val kind: String,
    val stage: String,
    val message: String,
    val userAgent: String,
    val device: String,
    val elapsedMs: Long,
    val version: String,
    val stack: String = "",
    val installId: String = "",
    val pageLoad: String = "",
    val occurrences: Int = 1,
    val context: Map<String, String> = emptyMap(),
    val breadcrumbs: List<String> = emptyList(),
)

/** Which reports to read: from [since] on, of these [kinds] (empty: all) and [version] (null: any). */
data class WebLogQuery(val since: Long = 0L, val kinds: Set<String> = emptySet(), val version: String? = null)

/** Where web reports are kept (newest first when read). */
interface WebLogStore {
    /** Keeps [log]; false when one with its id is already there (a report sent twice). */
    suspend fun add(log: WebLog): Boolean
    suspend fun recent(limit: Int, query: WebLogQuery = WebLogQuery()): List<WebLog>
    /** The report [id] happened [occurrences] times in its page load (never lowers the count). */
    suspend fun countOccurrences(id: String, occurrences: Int)
    /** The versions reported from [since] on, newest report first. */
    suspend fun versions(since: Long): List<String>
    /** Drops reports older than [before] (they are only useful while fresh). */
    suspend fun deleteOlderThan(before: Long)
    /** Drops the reports of [kind] (null: every kind); how many went. */
    suspend fun delete(kind: String?): Int
}
