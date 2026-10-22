package com.kglabs28.yapnplay.backend.domain.admin

/** What an admin may do. Read only overrides everything else. */
data class AdminPermissions(
    val readOnly: Boolean = false,
    val editSettings: Boolean = false,
    val removePlayers: Boolean = false,
    val createAdmins: Boolean = false,
) {
    fun allows(permission: AdminPermission): Boolean = when (permission) {
        AdminPermission.VIEW -> true
        AdminPermission.MODERATE -> !readOnly
        AdminPermission.EDIT_SETTINGS -> !readOnly && editSettings
        AdminPermission.REMOVE_PLAYERS -> !readOnly && removePlayers
        AdminPermission.CREATE_ADMINS -> !readOnly && createAdmins
    }

    companion object {
        val ALL = AdminPermissions(editSettings = true, removePlayers = true, createAdmins = true)
    }
}

/** One permission a route needs. MODERATE (block, coins, reports) is open to every admin who isn't read only. */
enum class AdminPermission { VIEW, MODERATE, EDIT_SETTINGS, REMOVE_PLAYERS, CREATE_ADMINS }

/** An admin account: its ID, salted password hash and permissions. */
data class AdminAccount(
    val adminId: String,
    val passwordHash: String,
    val salt: String,
    val permissions: AdminPermissions,
    val createdAt: Long,
    val createdBy: String,
)

/** Admin accounts and their sign-in sessions (only a hash of each session token is kept). */
interface AdminAccountStore {
    suspend fun count(): Long
    suspend fun find(adminId: String): AdminAccount?
    suspend fun all(): List<AdminAccount>
    /** False when the ID is taken. */
    suspend fun insert(account: AdminAccount): Boolean
    suspend fun updatePassword(adminId: String, passwordHash: String, salt: String)
    suspend fun createSession(tokenHash: String, adminId: String, expiresAt: Long)
    /** The account behind a live session, or null. */
    suspend fun sessionAccount(tokenHash: String, now: Long): AdminAccount?
    suspend fun deleteSession(tokenHash: String)
    /** Signs the admin out everywhere except [keepTokenHash]. */
    suspend fun deleteOtherSessions(adminId: String, keepTokenHash: String?)
}
