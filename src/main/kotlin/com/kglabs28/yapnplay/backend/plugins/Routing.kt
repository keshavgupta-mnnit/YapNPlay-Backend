package com.kglabs28.yapnplay.backend.plugins

import com.kglabs28.yapnplay.backend.api.http.roomStatsRoutes
import com.kglabs28.yapnplay.backend.api.http.wsTicketRoutes
import com.kglabs28.yapnplay.backend.api.http.openLinkRoutes
import com.kglabs28.yapnplay.backend.api.http.appAdsTxtRoutes
import com.kglabs28.yapnplay.backend.api.http.assetLinksRoutes
import com.kglabs28.yapnplay.backend.api.http.referralRoutes
import com.kglabs28.yapnplay.backend.api.http.feedbackRoutes
import com.kglabs28.yapnplay.backend.api.http.webLogRoutes
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
        publicRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), GUEST_RATE_LIMIT)
        webLogRoutes(koin.get(), WEB_LOG_RATE_LIMIT)
        openLinkRoutes()
        appAdsTxtRoutes()
        assetLinksRoutes()
        adminRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
        authenticate(BearerUserAuthProvider.NAME) {
            rateLimit(PLAYER_RATE_LIMIT) {
                userRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
                socialRoutes(koin.get(), koin.get(), koin.get(), koin.get())
                feedbackRoutes(koin.get())
                roomStatsRoutes(koin.get(), koin.get())
                wsTicketRoutes(koin.get())
                referralRoutes(koin.get())
            }
            webSocketRoutes(koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), koin.get())
        }
    }
}
