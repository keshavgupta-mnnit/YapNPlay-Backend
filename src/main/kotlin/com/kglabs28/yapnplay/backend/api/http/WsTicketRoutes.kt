package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.identity.WsTicketIssuer
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** `POST /auth/ws-ticket` (signed in): a one-time ticket for the web app's WebSocket. */
fun Route.wsTicketRoutes(tickets: WsTicketIssuer) {
    post(HttpPaths.WS_TICKET) { call.respond(tickets.issue(call.userId)) }
}
