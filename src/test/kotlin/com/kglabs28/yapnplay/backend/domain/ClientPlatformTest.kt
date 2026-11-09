package com.kglabs28.yapnplay.backend.domain

import com.kglabs28.yapnplay.backend.domain.identity.ClientPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientPlatformTest {

    @Test
    fun `the app's HTTP client (or no user agent) is the Android app`() {
        assertEquals(ClientPlatform(ClientPlatform.ANDROID_APP, null), ClientPlatform.fromUserAgent("ktor-client"))
        assertEquals(ClientPlatform(ClientPlatform.ANDROID_APP, null), ClientPlatform.fromUserAgent(null))
    }

    @Test
    fun `a browser is the web app, with its device's system`() {
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_IOS), web("Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_IOS), web("Mozilla/5.0 (iPad; CPU OS 18_2 like Mac OS X) AppleWebKit/605.1.15"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_ANDROID), web("Mozilla/5.0 (Linux; Android 15; Pixel 9) Chrome/131.0 Mobile Safari/537.36"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_WINDOWS), web("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0 Safari/537.36"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_MAC), web("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) Safari/605.1.15"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_LINUX), web("Mozilla/5.0 (X11; Linux x86_64) Firefox/133.0"))
        assertEquals(ClientPlatform(ClientPlatform.WEB, ClientPlatform.OS_OTHER), web("Mozilla/5.0 (PlayStation 5)"))
    }

    private fun web(userAgent: String) = ClientPlatform.fromUserAgent(userAgent)
}
