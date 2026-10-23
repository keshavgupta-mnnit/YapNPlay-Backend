package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.UsersTable
import com.kglabs28.yapnplay.backend.domain.identity.GameStats
import com.kglabs28.yapnplay.backend.infrastructure.db.tables.MatchResultSeatsTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** [UserRepository] on PostgreSQL. */
class ExposedUserRepository(private val db: DbQuery) : UserRepository {

    override suspend fun insert(user: User) {
        db.run {
            UsersTable.insert {
                it[id] = user.id.toUuid()
                it[displayName] = user.displayName
                it[secretHash] = user.secretHash
                it[createdAt] = user.createdAt
                it[xp] = user.xp
                it[userCode] = user.userCode
                it[avatarId] = user.avatarId
            }
        }
    }

    override suspend fun findById(id: String): User? {
        val uuid = id.toUuidOrNull() ?: return null
        return db.run {
            UsersTable.selectAll().where { UsersTable.id eq uuid }.singleOrNull()?.toUser()
        }
    }

    override suspend fun updateDisplayName(id: String, displayName: String): Boolean {
        val uuid = id.toUuidOrNull() ?: return false
        return db.run {
            UsersTable.update({ UsersTable.id eq uuid }) { it[UsersTable.displayName] = displayName } > 0
        }
    }

    override suspend fun addXp(id: String, delta: Int) {
        val uuid = id.toUuidOrNull() ?: return
        db.run {
            val current = UsersTable.selectAll().where { UsersTable.id eq uuid }.singleOrNull()?.get(UsersTable.xp) ?: return@run
            UsersTable.update({ UsersTable.id eq uuid }) { it[xp] = (current + delta).coerceAtLeast(0) }
        }
    }

    override suspend fun findByCode(userCode: String): User? =
        db.run { UsersTable.selectAll().where { UsersTable.userCode eq userCode }.singleOrNull()?.toUser() }

    override suspend fun updateProfile(id: String, displayName: String?, avatarId: String?) {
        val uuid = id.toUuidOrNull() ?: return
        db.run {
            UsersTable.update({ UsersTable.id eq uuid }) {
                displayName?.let { name -> it[UsersTable.displayName] = name }
                avatarId?.let { avatar -> it[UsersTable.avatarId] = avatar }
            }
        }
    }

    override suspend fun assignCode(id: String, code: String): Boolean = try {
        db.run { UsersTable.update({ (UsersTable.id eq id.toUuid()) and UsersTable.userCode.isNull() }) { it[userCode] = code } > 0 }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) false else throw e
    }

    override suspend fun findByPlayGamesId(playerId: String): User? =
        db.run { UsersTable.selectAll().where { UsersTable.playGamesPlayerId eq playerId }.singleOrNull()?.toUser() }

    override suspend fun linkPlayGames(id: String, playerId: String): Boolean = try {
        val uuid = id.toUuidOrNull() ?: return false
        db.run {
            UsersTable.update({ (UsersTable.id eq uuid) and UsersTable.playGamesPlayerId.isNull() }) {
                it[playGamesPlayerId] = playerId
            } > 0
        }
    } catch (e: ExposedSQLException) {
        if (e.sqlState == UNIQUE_VIOLATION) false else throw e
    }

    override suspend fun updateSecretHash(id: String, secretHash: String) {
        val uuid = id.toUuidOrNull() ?: return
        db.run { UsersTable.update({ UsersTable.id eq uuid }) { it[UsersTable.secretHash] = secretHash } }
    }

    override suspend fun statsOf(id: String): GameStats {
        val uuid = id.toUuidOrNull() ?: return GameStats(0, 0)
        return db.run {
            val rows = MatchResultSeatsTable.selectAll().where { MatchResultSeatsTable.userId eq uuid }.map { it[MatchResultSeatsTable.place] }
            GameStats(played = rows.size, won = rows.count { it == FIRST_PLACE })
        }
    }

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id].toString(),
        displayName = this[UsersTable.displayName],
        secretHash = this[UsersTable.secretHash],
        createdAt = this[UsersTable.createdAt],
        xp = this[UsersTable.xp],
        userCode = this[UsersTable.userCode],
        avatarId = this[UsersTable.avatarId],
    )

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val FIRST_PLACE = 1
    }
}
