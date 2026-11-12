package com.kglabs28.yapnplay.backend.domain.weblog

/**
 * An admin's decision on one issue ([fingerprint]): closed at [closedAt] by [closedBy], with a
 * [note]. An issue whose events come after [closedAt] has regressed.
 */
data class WebIssueState(val fingerprint: String, val closedAt: Long, val closedBy: String, val note: String)

/** Where issues' open/closed state is kept (only closed issues have a row). */
interface WebIssueStore {
    suspend fun closed(): Map<String, WebIssueState>
    suspend fun close(state: WebIssueState)
    suspend fun reopen(fingerprint: String)
}
