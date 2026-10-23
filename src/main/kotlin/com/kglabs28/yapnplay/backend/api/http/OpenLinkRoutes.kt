package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BackendConstants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Shared invites as https links (chat apps only make https tappable):
 * `/open/friend/<code>` and `/open/join/<code>` answer a page that opens
 * YapNPlay on that invite, or its Play Store page when it isn't installed.
 * Anything else goes to the store.
 */
fun Route.openLinkRoutes() {
    get(HttpPaths.OPEN_LINK) {
        val kind = call.parameters[HttpPaths.LINK_KIND_PARAM].orEmpty()
        val code = call.parameters[HttpPaths.LINK_CODE_PARAM].orEmpty()
        if (kind !in BackendConstants.LINK_KINDS || code.isEmpty() || code.length > BackendConstants.LINK_CODE_MAX_LENGTH || !code.all { it.isLetterOrDigit() }) {
            call.respondRedirect(BackendConstants.PLAY_STORE_URL)
            return@get
        }
        call.respondText(OpenAppPage.html(kind, code), ContentType.Text.Html, HttpStatusCode.OK)
    }
}
