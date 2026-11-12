package com.kglabs28.yapnplay.backend.application

import com.kglabs28.yapnplay.backend.application.weblog.WebAgent
import com.kglabs28.yapnplay.backend.application.weblog.WebIssueGrouping
import com.kglabs28.yapnplay.backend.application.weblog.WebStackDecoder
import com.kglabs28.yapnplay.backend.application.weblog.WebSymbolMap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Web crash stacks get their Kotlin names back from their own build's symbol map; without one they stay raw. */
class WebStackDecoderTest {
    private val map = WebSymbolMap.parse(
        sequenceOf("# web-v1.0.12", "2218:kotlin.createJsError", "5375:org.jetbrains.skia.Shader.makeLinearGradient", "5607:com.kglabs28.yapnplay.Glow.draw"),
    )!!
    private val decoder = WebStackDecoder { version -> map.takeIf { it.version == version } }
    private val safari = "kotlin.createJsError@https://www.yapnplay.in/yapnplay.js:1:1279\n2218@wasm-function[2218]\n5375@wasm-function[5375]\n5607@wasm-function[5607]\n9999@wasm-function[9999]"

    @Test
    fun `frames of the report's build get their names, unknown ones stay as they are`() = runBlocking {
        val decoded = decoder.decode(safari, "web-v1.0.12")
        assertEquals("", decoded.note)
        val lines = decoded.stack.lines()
        assertEquals("org.jetbrains.skia.Shader.makeLinearGradient  (wasm-function[5375])", lines[2])
        assertEquals("9999@wasm-function[9999]", lines[4])
        assertEquals("com.kglabs28.yapnplay.Glow.draw", WebIssueGrouping.where(decoded.stack))
    }

    @Test
    fun `a build without a map stays raw and says why`() = runBlocking {
        val decoded = decoder.decode(safari, "web-v1.0.11")
        assertEquals("", decoded.stack)
        assertTrue(decoded.note.contains("web-v1.0.11"))
        // A stack with no wasm frames (a plain JS error) needs no map at all.
        assertEquals("", decoder.decode("TypeError: x\n at f (a.js:1:2)", "web-v1.0.11").note)
    }

    @Test
    fun `a file that isn't a map is refused`() {
        assertNull(WebSymbolMap.fromGzip("not gzip".toByteArray()))
        assertNull(WebSymbolMap.parse(sequenceOf("# web-v1")))
    }

    @Test
    fun `the same error in two builds is one issue`() {
        assertEquals(
            WebIssueGrouping.fingerprint("CRASH", "RuntimeException: Can't wrap nullptr @ 1 < 2"),
            WebIssueGrouping.fingerprint("CRASH", "RuntimeException: Can't wrap nullptr @ 7 < 8 < 9"),
        )
    }

    @Test
    fun `user agents read as an OS and a browser`() {
        val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/27.2 Mobile/15E148 Safari/604.1"
        val windows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Safari/537.36"
        assertEquals("iOS 18.7" to "Safari 27.2", WebAgent.os(iphone) to WebAgent.browser(iphone))
        assertEquals("Windows" to "Chrome 157", WebAgent.os(windows) to WebAgent.browser(windows))
    }
}
