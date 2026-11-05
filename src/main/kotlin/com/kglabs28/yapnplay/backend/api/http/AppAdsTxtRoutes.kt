package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BackendConstants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/app-ads.txt` at the site's root: AdMob crawls it from the website on
 * the Play Store listing to confirm who may sell YapNPlay's ads. Without
 * it, ads are served less (lower fill and earnings).
 */
fun Route.appAdsTxtRoutes() {
    get(HttpPaths.APP_ADS_TXT) {
        call.respondText(BackendConstants.APP_ADS_TXT, ContentType.Text.Plain, HttpStatusCode.OK)
    }
}
