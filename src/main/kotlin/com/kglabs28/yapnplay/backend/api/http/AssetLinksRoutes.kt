package com.kglabs28.yapnplay.backend.api.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `/.well-known/assetlinks.json`: Android fetches it when the app is
 * installed and, if it vouches for the app, opens `/open/...` invite links
 * straight in YapNPlay instead of the browser.
 */
fun Route.assetLinksRoutes() {
    get(HttpPaths.ASSET_LINKS) {
        call.respondText(AssetLinks.json, ContentType.Application.Json, HttpStatusCode.OK)
    }
}
