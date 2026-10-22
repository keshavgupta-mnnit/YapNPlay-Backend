package com.kglabs28.yapnplay.backend.infrastructure.db

import com.kglabs28.yapnplay.backend.domain.admin.AdminAccount
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccountStore
import com.kglabs28.yapnplay.backend.domain.admin.AdminPermissions
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Mirrors V14__admin_accounts.sql. */
object AdminAccountsTable : Table("admin_accounts") {
    val adminId = varchar("admin_id", ID_MAX_LENGTH)
    val passwordHash = varchar("password_hash", HASH_MAX_LENGTH)
    val salt = varchar("salt", SALT_MAX_LENGTH)
    val readOnly = bool("read_only")
    val canEditSettings = bool("can_edit_settings")
    val canRemovePlayers = bool("can_remove_players")
    val canCreateAdmins = bool("can_create_admins")
    val createdAt = long("created_at")
    val createdBy = varchar("created_by", ID_MAX_LENGTH)

    override val primaryKey = PrimaryKey(adminId)

    const val ID_MAX_LENGTH = 32
    private const val HASH_MAX_LENGTH = 128
    private const val SALT_MAX_LENGTH = 64
}

/** Mirrors V14__admin_accounts.sql. */
object AdminSessionsTable : Table("admin_sessions") {
    val tokenHash = char("token_hash", SHA256_HEX_LENGTH)
    val adminId = varchar("admin_id", AdminAccountsTable.ID_MAX_LENGTH)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(tokenHash)

    private const val SHA256_HEX_LENGTH = 64
}

/** [AdminAccountStore] on PostgreSQL. */
class ExposedAdminAccountStore(private val db: DbQuery) : AdminAccountStore {

    override suspend fun count(): Long = db.run { AdminAccountsTable.selectAll().count() }

    override suspend fun find(adminId: String): AdminAccount? = db.run {
        AdminAccountsTable.selectAll().where { AdminAccountsTable.adminId eq adminId }.singleOrNull()?.toAccount()
    }

    override suspend fun all(): List<AdminAccount> = db.run {
        AdminAccountsTable.selectAll().orderBy(AdminAccountsTable.createdAt, SortOrder.ASC).map { it.toAccount() }
    }

    override suspend fun insert(account: AdminAccount): Boolean = db.run {
        if (AdminAccountsTable.selectAll().where { AdminAccountsTable.adminId eq account.adminId }.any()) return@run false
        AdminAccountsTable.insert {
            it[adminId] = account.adminId
            it[passwordHash] = account.passwordHash
            it[salt] = account.salt
            it[readOnly] = account.permissions.readOnly
            it[canEditSettings] = account.permissions.editSettings
            it[canRemovePlayers] = account.permissions.removePlayers
            it[canCreateAdmins] = account.permissions.createAdmins
            it[createdAt] = account.createdAt
            it[createdBy] = account.createdBy
        }
        true
    }

    override suspend fun updatePassword(adminId: String, passwordHash: String, salt: String) {
        db.run {
            AdminAccountsTable.update({ AdminAccountsTable.adminId eq adminId }) {
                it[AdminAccountsTable.passwordHash] = passwordHash
                it[AdminAccountsTable.salt] = salt
            }
        }
    }

    override suspend fun createSession(tokenHash: String, adminId: String, expiresAt: Long) {
        db.run {
            // Expired sessions are cleared as new ones start.
            AdminSessionsTable.deleteWhere { AdminSessionsTable.expiresAt less System.currentTimeMillis() }
            AdminSessionsTable.insert {
                it[AdminSessionsTable.tokenHash] = tokenHash
                it[AdminSessionsTable.adminId] = adminId
                it[AdminSessionsTable.expiresAt] = expiresAt
            }
        }
    }

    override suspend fun sessionAccount(tokenHash: String, now: Long): AdminAccount? = db.run {
        val session = AdminSessionsTable.selectAll()
            .where { (AdminSessionsTable.tokenHash eq tokenHash) and (AdminSessionsTable.expiresAt greater now) }
            .singleOrNull() ?: return@run null
        AdminAccountsTable.selectAll().where { AdminAccountsTable.adminId eq session[AdminSessionsTable.adminId] }.singleOrNull()?.toAccount()
    }

    override suspend fun deleteSession(tokenHash: String) {
        db.run { AdminSessionsTable.deleteWhere { AdminSessionsTable.tokenHash eq tokenHash } }
    }

    override suspend fun deleteOtherSessions(adminId: String, keepTokenHash: String?) {
        db.run {
            AdminSessionsTable.deleteWhere {
                if (keepTokenHash == null) AdminSessionsTable.adminId eq adminId
                else (AdminSessionsTable.adminId eq adminId) and (AdminSessionsTable.tokenHash neq keepTokenHash)
            }
        }
    }

    private fun ResultRow.toAccount() = AdminAccount(
        adminId = this[AdminAccountsTable.adminId],
        passwordHash = this[AdminAccountsTable.passwordHash],
        salt = this[AdminAccountsTable.salt],
        permissions = AdminPermissions(
            readOnly = this[AdminAccountsTable.readOnly],
            editSettings = this[AdminAccountsTable.canEditSettings],
            removePlayers = this[AdminAccountsTable.canRemovePlayers],
            createAdmins = this[AdminAccountsTable.canCreateAdmins],
        ),
        createdAt = this[AdminAccountsTable.createdAt],
        createdBy = this[AdminAccountsTable.createdBy],
    )
}
