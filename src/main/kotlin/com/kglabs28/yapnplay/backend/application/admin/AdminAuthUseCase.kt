package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccount
import com.kglabs28.yapnplay.backend.domain.admin.AdminAccountStore
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.admin.AdminPermissions
import com.kglabs28.yapnplay.backend.domain.common.Clock
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Admin sign-in by ID and password. Passwords are stored as a salted
 * PBKDF2 hash; a sign-in returns a random session token of which only a
 * SHA-256 hash is kept. The first sign-in attempt on an empty table creates
 * the starting account (admin / admin, every permission). Five wrong
 * passwords lock that ID for a minute.
 */
class AdminAuthUseCase(
    private val store: AdminAccountStore,
    private val audit: AdminAuditRepository,
    private val clock: Clock,
) {
    private val random = SecureRandom()
    private val failures = ConcurrentHashMap<String, Failures>()

    suspend fun login(adminId: String, password: String): AdminLoginResult {
        val id = adminId.trim().lowercase()
        val now = clock.nowMillis()
        if (store.count() == 0L) createAccount(FIRST_ADMIN_ID, FIRST_ADMIN_PASSWORD, AdminPermissions.ALL, SYSTEM)
        failures[id]?.let { if (it.count >= MAX_FAILURES && now < it.lockedUntil) throw ServiceException(ErrorCode.RATE_LIMITED, "Too many wrong passwords: try again in a minute") }
        val account = store.find(id)
        if (account == null || !matches(password, account)) {
            val f = failures.compute(id) { _, old -> Failures(((old?.count ?: 0) + 1), now + LOCK_MS) }!!
            audit.record(AdminAuditEntry(LOGIN_FAILED, id.take(TARGET_MAX), null, null, id.take(TARGET_MAX).ifEmpty { "?" }, if (f.count >= MAX_FAILURES) "locked for a minute" else null, now))
            throw ServiceException(ErrorCode.NOT_AUTHENTICATED, "Wrong ID or password")
        }
        failures.remove(id)
        val token = newToken()
        store.createSession(sha256(token), account.adminId, now + SESSION_MS)
        audit.record(AdminAuditEntry(LOGIN, account.adminId, null, null, account.adminId, null, now))
        return AdminLoginResult(token, account.view())
    }

    suspend fun logout(token: String) = store.deleteSession(sha256(token))

    /** The signed-in admin behind [token], or null. */
    suspend fun accountFor(token: String): AdminAccount? = store.sessionAccount(sha256(token), clock.nowMillis())

    suspend fun changePassword(account: AdminAccount, token: String?, current: String, new: String) {
        if (!matches(current, account)) throw ServiceException(ErrorCode.BAD_REQUEST, "The current password is wrong")
        requireStrong(new)
        val salt = newSalt()
        store.updatePassword(account.adminId, hash(new, salt), salt)
        // Every other sign-in of this admin ends.
        store.deleteOtherSessions(account.adminId, token?.let { sha256(it) })
        audit.record(AdminAuditEntry(PASSWORD, account.adminId, null, null, account.adminId, null, clock.nowMillis()))
    }

    suspend fun createAdmin(by: AdminAccount, adminId: String, password: String, permissions: AdminPermissions): AdminAccountView {
        val id = adminId.trim().lowercase()
        if (!ID_PATTERN.matches(id)) throw ServiceException(ErrorCode.BAD_REQUEST, "ID: 3 to 32 letters, digits, dots, dashes or underscores")
        requireStrong(password)
        val account = createAccount(id, password, permissions, by.adminId)
            ?: throw ServiceException(ErrorCode.BAD_REQUEST, "That ID is taken")
        audit.record(AdminAuditEntry(CREATE_ADMIN, id, null, describe(permissions), by.adminId, null, clock.nowMillis()))
        return account.view()
    }

    suspend fun admins(): List<AdminAccountView> = store.all().map { it.view() }

    private suspend fun createAccount(id: String, password: String, permissions: AdminPermissions, by: String): AdminAccount? {
        val salt = newSalt()
        val account = AdminAccount(id, hash(password, salt), salt, permissions, clock.nowMillis(), by)
        return if (store.insert(account)) account else null
    }

    private fun requireStrong(password: String) {
        if (password.length !in MIN_PASSWORD..MAX_PASSWORD) throw ServiceException(ErrorCode.BAD_REQUEST, "Password: $MIN_PASSWORD to $MAX_PASSWORD characters")
    }

    private fun matches(password: String, account: AdminAccount): Boolean =
        MessageDigest.isEqual(hash(password, account.salt).toByteArray(), account.passwordHash.toByteArray())

    private fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
        return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded)
    }

    private fun newSalt(): String = Base64.getEncoder().encodeToString(ByteArray(SALT_BYTES).also { random.nextBytes(it) })

    private fun newToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(TOKEN_BYTES).also { random.nextBytes(it) })

    private data class Failures(val count: Int, val lockedUntil: Long)

    companion object {
        const val FIRST_ADMIN_ID = "admin"
        private const val FIRST_ADMIN_PASSWORD = "admin"
        private const val SYSTEM = "system"
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 120_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val TOKEN_BYTES = 32
        private const val SESSION_MS = 12 * 60 * 60 * 1000L
        private const val LOCK_MS = 60_000L
        private const val MAX_FAILURES = 5
        private const val MIN_PASSWORD = 5
        private const val MAX_PASSWORD = 128
        private const val TARGET_MAX = 32
        private val ID_PATTERN = Regex("[a-z0-9._-]{3,32}")
        const val LOGIN = "LOGIN"
        const val LOGIN_FAILED = "LOGIN_FAILED"
        const val PASSWORD = "PASSWORD"
        const val CREATE_ADMIN = "CREATE_ADMIN"

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        fun describe(p: AdminPermissions): String = if (p.readOnly) "read only" else
            listOfNotNull("settings".takeIf { p.editSettings }, "remove".takeIf { p.removePlayers }, "admins".takeIf { p.createAdmins })
                .ifEmpty { listOf("moderate") }.joinToString(", ")
    }
}

/** An admin as the dashboard sees it (never the hash). */
@Serializable
data class AdminAccountView(
    val adminId: String,
    val readOnly: Boolean,
    val editSettings: Boolean,
    val removePlayers: Boolean,
    val createAdmins: Boolean,
    val createdAt: Long,
    val createdBy: String,
)

@Serializable
data class AdminLoginResult(val token: String, val admin: AdminAccountView)

fun AdminAccount.view() = AdminAccountView(
    adminId, permissions.readOnly, permissions.editSettings, permissions.removePlayers, permissions.createAdmins, createdAt, createdBy,
)
