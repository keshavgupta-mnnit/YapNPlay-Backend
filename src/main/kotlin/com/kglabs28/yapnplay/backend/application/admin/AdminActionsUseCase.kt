package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.admin.AdminDirectory
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.common.IdGenerator
import com.kglabs28.yapnplay.backend.domain.economy.CoinReason
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Penalty
import kotlinx.serialization.Serializable

/**
 * What the operator can do from the admin dashboard: find players, block
 * or unblock them, decide a report, and give or take coins. Every change
 * is written to the admin audit with who did it and why.
 */
class AdminActionsUseCase(
    private val directory: AdminDirectory,
    private val moderation: ModerationRepository,
    private val coins: CoinStore,
    private val audit: AdminAuditRepository,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun users(query: String): List<AdminUserView> {
        val now = clock.nowMillis()
        return directory.searchUsers(query, now, USER_LIMIT).map {
            AdminUserView(it.userId, it.displayName, it.userCode, it.coins, it.xp, it.createdAt, it.playGamesLinked, it.blockedUntil)
        }
    }

    suspend fun reports(): List<AdminReportView> = directory.recentReports(REPORT_LIMIT).map {
        AdminReportView(it.reportId, it.reporterName, it.reportedId, it.reportedName, it.reason, it.createdAt, it.upheld)
    }

    suspend fun auditLog(): List<AdminAuditView> = audit.recent(AUDIT_LIMIT).map {
        AdminAuditView(it.action, it.target, it.oldValue, it.newValue, it.changedBy, it.note, it.changedAt)
    }

    /** Blocks new matches for [days] (no XP taken). */
    suspend fun block(userId: String, days: Int, by: String, note: String?) {
        if (days !in 1..MAX_BLOCK_DAYS) throw ServiceException(ErrorCode.BAD_REQUEST, "Block for 1 to $MAX_BLOCK_DAYS days")
        requireUser(userId)
        val now = clock.nowMillis()
        val until = now + days * MILLIS_PER_DAY
        moderation.insertPenalty(Penalty(ids.newId(), userId, null, 0, until, now))
        audit.record(AdminAuditEntry(BLOCK, userId, null, "$days days", by, note, now))
    }

    suspend fun unblock(userId: String, by: String, note: String?) {
        requireUser(userId)
        val now = clock.nowMillis()
        moderation.clearBlocks(userId, now)
        audit.record(AdminAuditEntry(UNBLOCK, userId, null, null, by, note, now))
    }

    /** Gives (+) or takes (−) coins; never below 0. Returns the new balance. */
    suspend fun adjustCoins(userId: String, delta: Int, by: String, note: String?): Int {
        if (delta == 0 || delta !in -MAX_ADJUST..MAX_ADJUST) throw ServiceException(ErrorCode.BAD_REQUEST, "Change by 1 to $MAX_ADJUST coins")
        if (note.isNullOrBlank()) throw ServiceException(ErrorCode.BAD_REQUEST, "Say why the coins change")
        requireUser(userId)
        val now = clock.nowMillis()
        val old = coins.balance(userId)
        val balance = coins.change(userId, delta, CoinReason.ADMIN, null, now)
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, "They only have $old coins")
        audit.record(AdminAuditEntry(COINS, userId, old.toString(), balance.toString(), by, note, now))
        return balance
    }

    /** Decides a report still open (e.g. after a restart lost its vote). Upheld blocks the reported player. */
    suspend fun decideReport(reportId: String, upheld: Boolean, by: String, note: String?) {
        val now = clock.nowMillis()
        if (!directory.decideReport(reportId, upheld, now)) throw ServiceException(ErrorCode.BAD_REQUEST, "That report is already decided")
        if (upheld) {
            directory.reportedIn(reportId)?.let { reported ->
                moderation.insertPenalty(Penalty(ids.newId(), reported, reportId, 0, now + BackendConstants.PENALTY_BLOCK_MS, now))
            }
        }
        audit.record(AdminAuditEntry(REPORT, reportId, null, if (upheld) "upheld" else "rejected", by, note, now))
    }

    private suspend fun requireUser(userId: String) {
        if (directory.searchUsers(userId, clock.nowMillis(), 1).none { it.userId == userId }) {
            throw ServiceException(ErrorCode.NOT_FOUND, "No such player")
        }
    }

    private companion object {
        const val USER_LIMIT = 50
        const val REPORT_LIMIT = 50
        const val AUDIT_LIMIT = 100
        const val MAX_BLOCK_DAYS = 365
        const val MAX_ADJUST = 1_000_000
        const val MILLIS_PER_DAY = 86_400_000L
        const val BLOCK = "BLOCK"
        const val UNBLOCK = "UNBLOCK"
        const val COINS = "COINS"
        const val REPORT = "REPORT"
    }
}

@Serializable
data class AdminUserView(
    val userId: String,
    val displayName: String,
    val userCode: String?,
    val coins: Int,
    val xp: Int,
    val createdAt: Long,
    val playGamesLinked: Boolean,
    val blockedUntil: Long?,
)

@Serializable
data class AdminReportView(
    val reportId: String,
    val reporterName: String,
    val reportedId: String,
    val reportedName: String,
    val reason: String,
    val createdAt: Long,
    val upheld: Boolean?,
)

@Serializable
data class AdminAuditView(
    val action: String,
    val target: String,
    val oldValue: String?,
    val newValue: String?,
    val changedBy: String,
    val note: String?,
    val changedAt: Long,
)
