package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.moderation.Ballot
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Penalty
import com.kglabs28.yapnplay.backend.domain.moderation.ReportRecord
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PenaltiesTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.ReportBallotsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.ReportsTable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [ModerationRepository] on PostgreSQL. */
class ExposedModerationRepository(private val db: DbQuery) : ModerationRepository {

    override suspend fun insertReport(report: ReportRecord) {
        db.run {
            ReportsTable.insert {
                it[id] = report.id.toUuid()
                it[contextId] = report.contextId
                it[reporterId] = report.reporterId.toUuid()
                it[reportedId] = report.reportedId.toUuid()
                it[reason] = report.reason
                it[required] = report.required
                it[createdAt] = report.createdAt
            }
        }
    }

    override suspend fun resolve(reportId: String, upheld: Boolean, ballots: List<Ballot>, at: Long) {
        db.run {
            ballots.forEach { ballot ->
                ReportBallotsTable.insert {
                    it[this.reportId] = ballot.reportId.toUuid()
                    it[voterId] = ballot.voterId.toUuid()
                    it[response] = ballot.response
                    it[recordedAt] = ballot.recordedAt
                }
            }
            ReportsTable.update({ ReportsTable.id eq reportId.toUuid() }) {
                it[resolvedAt] = at
                it[this.upheld] = upheld
            }
        }
    }

    override suspend fun insertPenalty(penalty: Penalty) {
        db.run {
            PenaltiesTable.insert {
                it[id] = penalty.id.toUuid()
                it[userId] = penalty.userId.toUuid()
                it[reportId] = penalty.reportId?.toUuidOrNull()
                it[points] = penalty.points
                it[blockedUntil] = penalty.blockedUntil
                it[createdAt] = penalty.createdAt
            }
        }
    }

    override suspend fun activeBlock(userId: String, now: Long): Penalty? {
        val uuid = userId.toUuidOrNull() ?: return null
        return db.run {
            PenaltiesTable.selectAll()
                .where { (PenaltiesTable.userId eq uuid) and (PenaltiesTable.blockedUntil greater now) and PenaltiesTable.clearedAt.isNull() }
                .orderBy(PenaltiesTable.blockedUntil, SortOrder.DESC)
                .firstOrNull()
                ?.let {
                    Penalty(
                        id = it[PenaltiesTable.id].toString(),
                        userId = userId,
                        reportId = it[PenaltiesTable.reportId]?.toString(),
                        points = it[PenaltiesTable.points],
                        blockedUntil = it[PenaltiesTable.blockedUntil],
                        createdAt = it[PenaltiesTable.createdAt],
                    )
                }
        }
    }

    override suspend fun clearBlocks(userId: String, now: Long) {
        db.run {
            PenaltiesTable.update({ (PenaltiesTable.userId eq userId.toUuid()) and PenaltiesTable.clearedAt.isNull() }) {
                it[clearedAt] = now
            }
        }
    }
}
