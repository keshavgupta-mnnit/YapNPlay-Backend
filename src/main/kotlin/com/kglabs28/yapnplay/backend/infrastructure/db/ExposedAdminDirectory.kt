package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.admin.AdminDirectory
import com.kglabs28.yapnplay.backend.domain.admin.AdminReport
import com.kglabs28.yapnplay.backend.domain.admin.AdminUser
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PenaltiesTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.ReportsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [AdminDirectory] on PostgreSQL. */
class ExposedAdminDirectory(private val db: DbQuery) : AdminDirectory {

    override suspend fun searchUsers(query: String, now: Long, limit: Int): List<AdminUser> = db.run {
        val q = query.trim()
        val where: Op<Boolean> = when {
            q.isEmpty() -> Op.TRUE
            else -> {
                val byText = (UsersTable.displayName.lowerCase() like "%${q.lowercase()}%") or (UsersTable.userCode eq q.uppercase())
                q.toUuidOrNull()?.let { byText or (UsersTable.id eq it) } ?: byText
            }
        }
        val users = UsersTable.selectAll().where(where).orderBy(UsersTable.createdAt, SortOrder.DESC).limit(limit).toList()
        val ids = users.map { it[UsersTable.id] }
        val blocks = if (ids.isEmpty()) emptyMap() else PenaltiesTable.selectAll()
            .where { (PenaltiesTable.userId inList ids) and (PenaltiesTable.blockedUntil greater now) and PenaltiesTable.clearedAt.isNull() }
            .groupBy({ it[PenaltiesTable.userId] }, { it[PenaltiesTable.blockedUntil] })
            .mapValues { it.value.max() }
        users.map {
            AdminUser(
                userId = it[UsersTable.id].toString(),
                displayName = it[UsersTable.displayName],
                userCode = it[UsersTable.userCode],
                coins = it[UsersTable.coins],
                xp = it[UsersTable.xp],
                createdAt = it[UsersTable.createdAt],
                playGamesLinked = it[UsersTable.playGamesPlayerId] != null,
                blockedUntil = blocks[it[UsersTable.id]],
            )
        }
    }

    override suspend fun recentReports(limit: Int): List<AdminReport> = db.run {
        val reports = ReportsTable.selectAll().orderBy(ReportsTable.createdAt, SortOrder.DESC).limit(limit).toList()
        val ids = reports.flatMap { listOf(it[ReportsTable.reporterId], it[ReportsTable.reportedId]) }.distinct()
        val names = if (ids.isEmpty()) emptyMap() else UsersTable.selectAll().where { UsersTable.id inList ids }
            .associate { it[UsersTable.id] to it[UsersTable.displayName] }
        reports.map {
            AdminReport(
                reportId = it[ReportsTable.id].toString(),
                reporterName = names[it[ReportsTable.reporterId]].orEmpty(),
                reportedId = it[ReportsTable.reportedId].toString(),
                reportedName = names[it[ReportsTable.reportedId]].orEmpty(),
                reason = it[ReportsTable.reason],
                createdAt = it[ReportsTable.createdAt],
                upheld = it[ReportsTable.upheld],
            )
        }
    }

    override suspend fun decideReport(reportId: String, upheld: Boolean, at: Long): Boolean {
        val uuid = reportId.toUuidOrNull() ?: return false
        return db.run {
            ReportsTable.update({ (ReportsTable.id eq uuid) and ReportsTable.resolvedAt.isNull() }) {
                it[resolvedAt] = at
                it[ReportsTable.upheld] = upheld
            } > 0
        }
    }

    override suspend fun reportedIn(reportId: String): String? {
        val uuid = reportId.toUuidOrNull() ?: return null
        return db.run { ReportsTable.selectAll().where { ReportsTable.id eq uuid }.singleOrNull()?.get(ReportsTable.reportedId)?.toString() }
    }
}
