package com.kglabs28.yapnplay.backend.domain.identity

/**
 * Where a sign-in came from: [platform] is the Android app or the web app;
 * for the web, [os] is the device's system as its browser tells it. Read
 * from the request's User-Agent: browsers send one starting "Mozilla/", the
 * app's HTTP client doesn't.
 */
data class ClientPlatform(val platform: String, val os: String?) {

    companion object {
        const val ANDROID_APP = "android"
        const val WEB = "web"

        const val OS_IOS = "ios"
        const val OS_ANDROID = "android"
        const val OS_WINDOWS = "windows"
        const val OS_MAC = "mac"
        const val OS_LINUX = "linux"
        const val OS_OTHER = "other"

        fun fromUserAgent(userAgent: String?): ClientPlatform {
            val ua = userAgent.orEmpty()
            if (!ua.startsWith(BROWSER_PREFIX)) return ClientPlatform(ANDROID_APP, null)
            val os = when {
                IOS_DEVICES.any { it in ua } -> OS_IOS
                ANDROID in ua -> OS_ANDROID
                WINDOWS in ua -> OS_WINDOWS
                MAC in ua -> OS_MAC
                LINUX in ua || CROS in ua -> OS_LINUX
                else -> OS_OTHER
            }
            return ClientPlatform(WEB, os)
        }

        private const val BROWSER_PREFIX = "Mozilla/"
        private val IOS_DEVICES = listOf("iPhone", "iPad", "iPod")
        private const val ANDROID = "Android"
        private const val WINDOWS = "Windows"
        private const val MAC = "Macintosh"
        private const val LINUX = "Linux"
        private const val CROS = "CrOS"
    }
}
