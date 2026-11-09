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
 * `/open/friend/<code>` and `/open/join/<code>`, optionally `?ref=<userCode>`
 * (who shared it), answer a page that opens YapNPlay on that invite, its Play
 * Store page when it isn't installed, or the web app off Android. Anything
 * else goes to the store; a malformed `ref` is dropped.
 */
fun Route.openLinkRoutes() {
    get(HttpPaths.OPEN_LINK) {
        val kind = call.parameters[HttpPaths.LINK_KIND_PARAM].orEmpty()
        val code = call.parameters[HttpPaths.LINK_CODE_PARAM].orEmpty()
        if (kind !in BackendConstants.LINK_KINDS || !code.isLinkCode()) {
            call.respondRedirect(BackendConstants.PLAY_STORE_URL)
            return@get
        }
        val ref = call.request.queryParameters[BackendConstants.LINK_REF_PARAM]?.takeIf { it.isLinkCode() }
        call.respondText(OpenAppPage.html(kind, code, ref), ContentType.Text.Html, HttpStatusCode.OK)
    }
}

private fun String.isLinkCode(): Boolean =
    isNotEmpty() && length <= BackendConstants.LINK_CODE_MAX_LENGTH && all { it.isLetterOrDigit() && it.code < ASCII_LIMIT }

/** Letters and digits from plain ASCII only, so a code is safe inside the page's HTML and script. */
private const val ASCII_LIMIT = 128
