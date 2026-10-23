package com.kglabs28.yapnplay.backend.api.http

import com.kglabs28.yapnplay.backend.config.BackendConstants
import java.net.URLEncoder

/**
 * The page behind a shared invite link: on Android it hands straight over
 * to the app (`intent://<kind>/<code>`), and Chrome falls back to the Play
 * Store when the app isn't there. A button does the same if the hand-over
 * was blocked; the code is shown to type in by hand. [kind] and [code] are
 * checked (letters and digits only) before they get here.
 */
object OpenAppPage {
    fun html(kind: String, code: String): String {
        val store = BackendConstants.PLAY_STORE_URL
        val fallback = URLEncoder.encode(store, Charsets.UTF_8)
        val intent = "intent://$kind/$code#Intent;scheme=${BackendConstants.APP_SCHEME};package=${BackendConstants.APP_PACKAGE};S.browser_fallback_url=$fallback;end"
        val what = if (kind == "friend") "Add friend $code" else "Join room $code"
        return """
            <!doctype html>
            <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>YapNPlay</title>
            <style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#0b0d1e;color:#fff;font-family:sans-serif;text-align:center}
            a{display:block;margin:12px auto;padding:14px 24px;border-radius:999px;background:#1fae6b;color:#fff;text-decoration:none;font-weight:bold;max-width:260px}
            a.store{background:#2a2f55}</style></head>
            <body><div><h2>YapNPlay</h2><p>$what</p>
            <a href="$intent">Open in YapNPlay</a><a class="store" href="$store">Get YapNPlay</a>
            <p>Or enter <b>$code</b> in the app.</p></div>
            <script>if (/Android/i.test(navigator.userAgent)) location.replace("$intent");</script>
            </body></html>
        """.trimIndent()
    }
}
