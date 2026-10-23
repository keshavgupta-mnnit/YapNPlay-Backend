package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.admin.AdminDirectory
import com.kglabs28.yapnplay.backend.domain.admin.AdminRoom
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.BlocksTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.DevicesTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FriendRequestsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.FriendshipsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.JoinRequestsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.MatchResultSeatsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PlayRoomMembersTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.PlayRoomsTable
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.ReportBallotsTable
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.deleteWhere
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
        val part = "%${q.lowercase()}%"
        val where: Op<Boolean> = if (q.isEmpty()) Op.TRUE else
            (UsersTable.displayName.lowerCase() like part) or (UsersTable.userCode.lowerCase() like part) or
                (UsersTable.playGamesPlayerId.lowerCase() like part) or
                (UsersTable.id.castTo<String>(TextColumnType()) like part)
        val users = UsersTable.selectAll().where(where).orderBy(UsersTable.createdAt, SortOrder.DESC).limit(limit).toList()
        val ids = users.map { it[UsersTable.id] }
        val blocks = if (ids.isEmpty()) emptyMap() else PenaltiesTable.selectAll()
            .where { (PenaltiesTable.userId inList ids) and (PenaltiesTable.blockedUntil greater now) and PenaltiesTable.clearedAt.isNull() }
            .groupBy({ it[PenaltiesTable.userId] }, { it[PenaltiesTable.blockedUntil] })
            .mapValues { it.value.max() }
        val friends = if (ids.isEmpty()) emptyMap() else FriendshipsTable.selectAll().where { FriendshipsTable.userId inList ids }
            .groupingBy { it[FriendshipsTable.userId] }.eachCount()
        val groups = if (ids.isEmpty()) emptyMap() else (PlayRoomMembersTable innerJoin PlayRoomsTable).selectAll()
            .where { PlayRoomMembersTable.userId inList ids }
            .groupBy({ it[PlayRoomMembersTable.userId] }, { it[PlayRoomsTable.name] })
        val games = if (ids.isEmpty()) emptyMap() else MatchResultSeatsTable.selectAll().where { MatchResultSeatsTable.userId inList ids }
            .groupingBy { it[MatchResultSeatsTable.userId] }.eachCount()
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
                friends = friends[it[UsersTable.id]] ?: 0,
                groups = groups[it[UsersTable.id]].orEmpty().sorted(),
                gamesPlayed = games[it[UsersTable.id]] ?: 0,
                onlineMs = it[UsersTable.onlineMs],
                lastSeenAt = it[UsersTable.lastSeenAt],
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

    override suspend fun searchRooms(query: String, limit: Int): List<AdminRoom> = db.run {
        val q = query.trim()
        val part = "%${q.lowercase()}%"
        val where: Op<Boolean> = if (q.isEmpty()) Op.TRUE else
            (PlayRoomsTable.name.lowerCase() like part) or (PlayRoomsTable.code.lowerCase() like part) or
                (PlayRoomsTable.description.lowerCase() like part) or (PlayRoomsTable.selectedGame.lowerCase() like part) or
                (PlayRoomsTable.id.castTo<String>(TextColumnType()) like part)
        val rooms = PlayRoomsTable.selectAll().where(where).orderBy(PlayRoomsTable.createdAt, SortOrder.DESC).limit(limit).toList()
        val roomIds = rooms.map { it[PlayRoomsTable.id] }
        val counts = if (roomIds.isEmpty()) emptyMap() else PlayRoomMembersTable.selectAll().where { PlayRoomMembersTable.roomId inList roomIds }
            .groupingBy { it[PlayRoomMembersTable.roomId] }.eachCount()
        val people = rooms.flatMap { listOf(it[PlayRoomsTable.leaderId], it[PlayRoomsTable.adminId]) }.distinct()
        val names = if (people.isEmpty()) emptyMap() else UsersTable.selectAll().where { UsersTable.id inList people }
            .associate { it[UsersTable.id] to it[UsersTable.displayName] }
        rooms.map {
            AdminRoom(
                roomId = it[PlayRoomsTable.id].toString(),
                code = it[PlayRoomsTable.code],
                name = it[PlayRoomsTable.name],
                description = it[PlayRoomsTable.description],
                selectedGame = it[PlayRoomsTable.selectedGame],
                members = counts[it[PlayRoomsTable.id]] ?: 0,
                leaderName = names[it[PlayRoomsTable.leaderId]].orEmpty(),
                adminName = names[it[PlayRoomsTable.adminId]].orEmpty(),
                createdAt = it[PlayRoomsTable.createdAt],
            )
        }
    }

    override suspend fun deleteUser(userId: String): Boolean {
        val id = userId.toUuidOrNull() ?: return false
        return db.run {
            if (UsersTable.selectAll().where { UsersTable.id eq id }.empty()) return@run false
            // Rooms the player created but others still use pass to the room's admin.
            PlayRoomsTable.selectAll().where { PlayRoomsTable.createdBy eq id }.toList().forEach { room ->
                PlayRoomsTable.update({ PlayRoomsTable.id eq room[PlayRoomsTable.id] }) { it[createdBy] = room[PlayRoomsTable.adminId] }
            }
            val reports = ReportsTable.selectAll().where { (ReportsTable.reporterId eq id) or (ReportsTable.reportedId eq id) }.map { it[ReportsTable.id] }
            PenaltiesTable.deleteWhere { PenaltiesTable.userId eq id }
            if (reports.isNotEmpty()) {
                PenaltiesTable.deleteWhere { PenaltiesTable.reportId inList reports }
                ReportBallotsTable.deleteWhere { ReportBallotsTable.reportId inList reports }
                ReportsTable.deleteWhere { ReportsTable.id inList reports }
            }
            ReportBallotsTable.deleteWhere { ReportBallotsTable.voterId eq id }
            FriendshipsTable.deleteWhere { (FriendshipsTable.userId eq id) or (FriendshipsTable.friendId eq id) }
            FriendRequestsTable.deleteWhere { (FriendRequestsTable.fromId eq id) or (FriendRequestsTable.toId eq id) }
            BlocksTable.deleteWhere { (BlocksTable.blockerId eq id) or (BlocksTable.blockedId eq id) }
            DevicesTable.deleteWhere { DevicesTable.userId eq id }
            JoinRequestsTable.deleteWhere { JoinRequestsTable.userId eq id }
            PlayRoomMembersTable.deleteWhere { PlayRoomMembersTable.userId eq id }
            MatchResultSeatsTable.update({ MatchResultSeatsTable.userId eq id }) { it[MatchResultSeatsTable.userId] = null }
            UsersTable.deleteWhere { UsersTable.id eq id } > 0
        }
    }

    override suspend fun reportedIn(reportId: String): String? {
        val uuid = reportId.toUuidOrNull() ?: return null
        return db.run { ReportsTable.selectAll().where { ReportsTable.id eq uuid }.singleOrNull()?.get(ReportsTable.reportedId)?.toString() }
    }
}
