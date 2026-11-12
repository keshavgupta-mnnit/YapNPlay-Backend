package com.kglabs28.yapnplay.backend.application.weblog

/** A browser's user agent read as an OS ("iOS 18.7") and a browser ("Safari 27.2"), for reports. */
object WebAgent {

    fun os(agent: String): String = when {
        IOS.containsMatchIn(agent) -> "iOS " + IOS.find(agent)!!.groupValues[1].replace('_', '.')
        ANDROID.containsMatchIn(agent) -> "Android " + ANDROID.find(agent)!!.groupValues[1]
        "Windows" in agent -> "Windows"
        MAC.containsMatchIn(agent) -> "macOS"
        "CrOS" in agent -> "ChromeOS"
        "Linux" in agent -> "Linux"
        else -> "Other"
    }

    fun browser(agent: String): String {
        BROWSERS.forEach { (name, pattern) -> pattern.find(agent)?.let { return name + " " + it.groupValues[1] } }
        return "Other"
    }

    private val IOS = Regex("""(?:iPhone|iPad|iPod).*? OS (\d+(?:_\d+)?)""")
    private val ANDROID = Regex("""Android (\d+(?:\.\d+)?)""")
    private val MAC = Regex("""Mac OS X|Macintosh""")

    // Order matters: Edge, Opera and the iPhone's Chrome/Firefox all say "Safari" or "Chrome" too.
    private val BROWSERS = listOf(
        "Edge" to Regex("""Edg(?:e|A|iOS)?/(\d+)"""),
        "Opera" to Regex("""OPR/(\d+)"""),
        "Samsung" to Regex("""SamsungBrowser/(\d+)"""),
        "Chrome" to Regex("""(?:Chrome|CriOS)/(\d+)"""),
        "Firefox" to Regex("""(?:Firefox|FxiOS)/(\d+)"""),
        "Safari" to Regex("""Version/(\d+(?:\.\d+)?).*Safari"""),
    )
}
