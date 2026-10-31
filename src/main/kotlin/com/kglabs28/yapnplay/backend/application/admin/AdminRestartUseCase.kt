package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditEntry
import com.kglabs28.yapnplay.backend.domain.admin.AdminAuditRepository
import com.kglabs28.yapnplay.backend.domain.admin.ServerRestarter

/** An admin restarts the server: audited first, then the server stops and the host starts it again. */
class AdminRestartUseCase(
    private val audit: AdminAuditRepository,
    private val restarter: ServerRestarter,
) {
    suspend operator fun invoke(by: String, note: String?) {
        audit.record(AdminAuditEntry(RESTART, SERVER, null, null, by, note, System.currentTimeMillis()))
        restarter.restartSoon()
    }

    private companion object {
        const val RESTART = "RESTART"
        const val SERVER = "server"
    }
}
