package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BackendConstants
import java.net.URLEncoder

/**
 * The page behind a shared invite link. The invite ([kind] + [code], and
 * [ref], the sharer's user code, for the invite bonus) travels on every way
 * out, so nobody has to tap the link twice:
 * - Android with the app: straight into it (`intent://<kind>/<code>?ref=`).
 * - Android without it: Chrome falls back to the Play Store with the invite
 *   as the install referrer; the app reads it on first launch.
 * - Anything else (iPhone, computers): the web app, `?open=<kind>/<code>&ref=`.
 * Buttons do the same if the hand-over was blocked; the code is shown to type
 * in by hand. [kind], [code] and [ref] are checked (letters and digits only)
 * before they get here.
 */
object OpenAppPage {
    fun html(kind: String, code: String, ref: String?): String {
        val invite = inviteQuery(kind, code, ref)
        val store = BackendConstants.PLAY_STORE_URL + "&" + BackendConstants.STORE_REFERRER_PARAM + "=" + encode(invite)
        val web = BackendConstants.WEB_APP_URL + "?" + invite
        val appUri = "$kind/$code" + (ref?.let { "?${BackendConstants.LINK_REF_PARAM}=$it" } ?: "")
        val intent = "intent://$appUri#Intent;scheme=${BackendConstants.APP_SCHEME};package=${BackendConstants.APP_PACKAGE};S.browser_fallback_url=${encode(store)};end"
        val what = if (kind == "friend") "Add friend $code" else "Join room $code"
        return """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>YapNPlay</title>
            <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0b0d1e;color:#fff;font-family:sans-serif;text-align:center}
            a{display:block;margin:12px auto;padding:14px 24px;border-radius:999px;background:#1fae6b;color:#fff;text-decoration:none;font-weight:bold;max-width:260px}
            a.store{background:#2a2f55}</style></head>
            <body><div><h2>YapNPlay</h2><p>$what</p>
            <a href="$intent">Open in YapNPlay</a><a class="store" href="$store">Get YapNPlay</a><a class="store" href="$web">Play in the browser</a>
            <p>Or enter <b>$code</b> in the app.</p></div>
            <script>location.replace(/Android/i.test(navigator.userAgent) ? "$intent" : "$web");</script>
            </body></html>
        """.trimIndent()
    }

    /** `open=<kind>/<code>&ref=<userCode>`: the invite as the web app and the install referrer read it. */
    private fun inviteQuery(kind: String, code: String, ref: String?): String =
        "${BackendConstants.WEB_OPEN_PARAM}=$kind/$code" + (ref?.let { "&${BackendConstants.LINK_REF_PARAM}=$it" } ?: "")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
