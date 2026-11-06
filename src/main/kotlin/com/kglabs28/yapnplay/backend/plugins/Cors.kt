package com.kglabs28.yapnplay.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

/**
 * The web app is served from another origin, so browsers ask before each
 * API call. Only the configured [origins] (WEB_APP_ORIGINS) are allowed;
 * with none set, browsers are refused as before. The Android app is not a
 * browser and is never affected.
 */
fun Application.configureCors(origins: Set<String>) {
    if (origins.isEmpty()) return
    install(CORS) {
        origins.forEach { origin ->
            val scheme = origin.substringBefore(SCHEME_SEPARATOR)
            allowHost(origin.substringAfter(SCHEME_SEPARATOR), schemes = listOf(scheme))
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
}

private const val SCHEME_SEPARATOR = "://"
