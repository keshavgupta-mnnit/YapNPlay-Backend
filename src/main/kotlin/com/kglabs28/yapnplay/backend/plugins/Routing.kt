package com.kglabs28.yapnplay.backend.plugins

import io.ktor.server.plugins.ratelimit.rateLimit
import com.kglabs28.yapnplay.backend.api.auth.BearerUserAuthProvider
import com.kglabs28.yapnplay.backend.api.http.adminRoutes
import com.kglabs28.yapnplay.backend.api.http.publicRoutes
import com.kglabs28.yapnplay.backend.api.http.socialRoutes
import com.kglabs28.yapnplay.backend.api.http.userRoutes
import com.kglabs28.yapnplay.backend.api.ws.webSocketRoutes
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import org.koin.core.Koin

/** All routes, with their use cases taken from the dependency graph. */
fun Application.configureRouting(koin: Koin) {
    routing {
        publicRoutes(koin.get(), GUEST_RATE_LIMIT)
        adminRoutes(koin.get(), koin.get())
        authenticate(BearerUserAuthProvider.NAME) {
            rateLimit(PLAYER_RATE_LIMIT) {
                userRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
                socialRoutes(koin.get(), koin.get(), koin.get(), koin.get())
            }
            webSocketRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
        }
    }
}
