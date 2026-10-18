package com.kglabs28.yapnplay.backend.plugins

import com.kglabs28.yapnplay.backend.config.BackendConstants
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

/**
 * WebSockets with a heartbeat: the server pings every 15 s and drops a
 * connection that has been silent for 45 s.
 */
fun Application.configureSockets() {
    install(WebSockets) {
        pingPeriodMillis = BackendConstants.WS_PING_SECONDS * BackendConstants.MILLIS_PER_SECOND
        timeoutMillis = BackendConstants.WS_TIMEOUT_SECONDS * BackendConstants.MILLIS_PER_SECOND
        maxFrameSize = BackendConstants.WS_MAX_FRAME_BYTES
        masking = false
    }
}
