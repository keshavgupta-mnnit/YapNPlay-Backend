package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.WsTicket
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.identity.SecretGenerator
import java.util.concurrent.ConcurrentHashMap

/**
 * One-time WebSocket tickets (contract 0.12.5). Browsers cannot put a
 * header on a WebSocket, so the web app asks for a ticket over HTTP (with
 * its bearer) and opens `/ws?ticket=...`: the account's real secret never
 * goes in a URL. A ticket works once, within [BackendConstants.WS_TICKET_SECONDS].
 */
class WsTicketIssuer(
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {
    private class Issued(val userId: String, val expiresAt: Long)

    private val issued = ConcurrentHashMap<String, Issued>()

    fun issue(userId: String): WsTicket {
        val now = clock.nowMillis()
        // Old unused tickets go, so the map never grows.
        issued.entries.removeIf { it.value.expiresAt <= now }
        val ticket = secrets.newSecret()
        val expiresAt = now + BackendConstants.WS_TICKET_SECONDS * MILLIS_PER_SECOND
        issued[ticket] = Issued(userId, expiresAt)
        return WsTicket(ticket, expiresAt)
    }

    /** The ticket's user, once: null when unknown, used or expired. */
    fun redeem(ticket: String): String? {
        val found = issued.remove(ticket) ?: return null
        return found.userId.takeIf { found.expiresAt > clock.nowMillis() }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
