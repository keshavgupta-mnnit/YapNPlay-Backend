package com.kglabs28.yapnplay.backend.api.ws

import com.kglabs28.yapnplay.backend.api.auth.userId
import com.kglabs28.yapnplay.backend.application.realtime.EnvelopeCodec
import com.kglabs28.yapnplay.backend.application.rooms.ExitCurrentRoomUseCase
import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.domain.common.Clock
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * `GET /ws` (signed in). Reads one message at a time, so each user's
 * requests are handled in the order they were sent. When the connection
 * ends (closed, or silent past the heartbeat timeout) the user stops
 * being present in their room but stays a member.
 */
fun Route.webSocketRoutes(
    registry: ConnectionRegistry,
    router: EnvelopeRouter,
    codec: EnvelopeCodec,
    exitCurrentRoom: ExitCurrentRoomUseCase,
    clock: Clock,
    hooks: ConnectionHooks,
) {
    webSocket(BackendConstants.WS_PATH) {
        val userId = call.userId
        // A newer app lists its games, so a room never starts a game someone's app can't show.
        val games = call.request.headers[BackendConstants.APP_GAMES_HEADER]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val connection = ClientConnection(userId, this, games)
        registry.register(connection)?.close(
            CloseReason(BackendConstants.WS_SESSION_REPLACED_CODE, BackendStrings.SESSION_REPLACED),
        )
        val writer = launch { connection.pumpOutgoing() }
        launch { runCatching { hooks.connected(userId) } }
        val inbound = InboundGate(codec, MessageRateLimiter(clock), RecentReplies())
        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val reply = inbound.process(frame.readText()) { router.handle(userId, it) }
                connection.offer(codec.encode(reply))
            }
        } finally {
            connection.stop()
            writer.cancel()
            if (registry.unregister(connection)) {
                withContext(NonCancellable) {
                    runCatching { exitCurrentRoom(userId, force = true) }
                    runCatching { hooks.disconnected(userId) }
                }
            }
        }
    }
}
