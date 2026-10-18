package com.kglabs28.yapnplay.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * JSON bodies, request logging and proxy headers. Logs show method, path
 * and status only: never headers or query strings (they can hold secrets).
 */
fun Application.configureHttp(json: Json) {
    install(ContentNegotiation) { json(json) }
    // Behind a host's proxy (Render, Caddy) the caller's address arrives in X-Forwarded-For.
    install(XForwardedHeaders)
    install(CallLogging) {
        format { call -> "${call.response.status()}: ${call.request.httpMethod.value} ${call.request.path()}" }
    }
}
