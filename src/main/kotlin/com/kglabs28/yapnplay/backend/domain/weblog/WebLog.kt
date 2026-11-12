package com.kglabs28.yapnplay.backend.domain.weblog

/**
 * One report from a web browser: [kind] (FAILED: it broke before the first screen; SLOW: still
 * loading; STARTED: the first screen showed; CRASH: an error inside the running app), how far it
 * got ([stage]), what went wrong ([message]), the browser ([userAgent]), the device ([device]:
 * memory, cores, screen), the time since the page opened ([elapsedMs]), the web build ([version])
 * and, for an error, its full stack trace ([stack], one frame a line; empty when there is none).
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
)

/** Where web reports are kept (newest first when read). */
interface WebLogStore {
    suspend fun add(log: WebLog)
    suspend fun recent(limit: Int, kind: String? = null): List<WebLog>
    /** Drops reports older than [before] (they are only useful while fresh). */
    suspend fun deleteOlderThan(before: Long)
    /** Drops the reports of [kind] (null: every kind) with exactly [message] (null: any); how many went. */
    suspend fun delete(kind: String?, message: String?): Int
}
