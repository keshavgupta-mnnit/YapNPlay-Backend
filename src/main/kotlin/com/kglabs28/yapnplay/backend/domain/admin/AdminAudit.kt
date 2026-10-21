package com.kglabs28.yapnplay.backend.domain.admin

/** One change made from the admin dashboard. */
data class AdminAuditEntry(
    val action: String,
    val target: String,
    val oldValue: String?,
    val newValue: String?,
    val changedBy: String,
    val note: String?,
    val changedAt: Long,
)

/** The admin audit trail (who changed what, and when). */
interface AdminAuditRepository {
    suspend fun record(entry: AdminAuditEntry)
    suspend fun recent(limit: Int): List<AdminAuditEntry>
}
