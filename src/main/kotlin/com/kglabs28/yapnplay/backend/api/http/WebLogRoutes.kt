package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.application.weblog.WebLogBody
import com.kglabs28.yapnplay.backend.application.weblog.WebLogUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

/**
 * POST /web/log: what a browser reports while the web app loads and runs (no sign-in: it may
 * not have started). Sent as plain text (navigator.sendBeacon), so no CORS preflight; JSON
 * inside. Per-address rate limited; anything too big or unreadable is ignored.
 */
fun Route.webLogRoutes(webLogs: WebLogUseCase, limit: RateLimitName) {
    rateLimit(limit) {
        post(HttpPaths.WEB_LOG) {
            val text = call.receiveText()
            if (text.length <= MAX_BODY) {
                runCatching { JSON.decodeFromString(WebLogBody.serializer(), text) }.getOrNull()?.let { webLogs.record(it) }
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private val JSON = Json { ignoreUnknownKeys = true }
private const val MAX_BODY = 10_000
