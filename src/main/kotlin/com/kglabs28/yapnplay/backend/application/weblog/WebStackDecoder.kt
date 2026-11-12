package com.kglabs28.yapnplay.backend.application.weblog

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Turns a web crash stack's wasm function numbers (`wasm-function[5375]`) into Kotlin names, with
 * the symbol map of the report's own build ([symbols]: its release uploads one). The numbers change
 * with every build, so each report uses its build's map; without one the stack stays as it is.
 */
class WebStackDecoder(private val symbols: suspend (version: String) -> WebSymbolMap?) {

    /** The decoded stack, or why it couldn't be. */
    suspend fun decode(stack: String, version: String): WebStackDecoded {
        if (stack.isBlank() || FRAME.find(stack) == null) return WebStackDecoded("", "")
        val map = version.takeIf { it.isNotBlank() }?.let { symbols(it) }
            ?: return WebStackDecoded("", "No symbol map for ${version.ifBlank { "this build" }} yet (its web release uploads one).")
        val decoded = stack.lines().joinToString("\n") { line ->
            FRAME.find(line)?.let { frame -> map.names[frame.groupValues[1].toInt()]?.let { "$it  (${frame.value})" } } ?: line
        }
        return WebStackDecoded(decoded, "")
    }

    private companion object {
        val FRAME = Regex("""wasm-function\[(\d+)]""")
    }
}

/** A stack with names ([stack], empty when not decoded) and, when it isn't, why ([note]). */
data class WebStackDecoded(val stack: String, val note: String)

/** One web build's function numbers and names; [version] is the build (its release tag). */
class WebSymbolMap(val version: String, val names: Map<Int, String>) {
    companion object {
        /** The gzipped file a release uploads, or null when it isn't one. */
        fun fromGzip(bytes: ByteArray): WebSymbolMap? = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().useLines { parse(it) }
        }.getOrNull()

        /** "# web-v1.0.12" then "5375:org.jetbrains.skia.Shader.makeLinearGradient" a line. */
        fun parse(lines: Sequence<String>): WebSymbolMap? {
            var version: String? = null
            val names = HashMap<Int, String>()
            lines.forEach { line ->
                when {
                    line.startsWith("#") -> if (version == null) version = line.removePrefix("#").trim()
                    else -> {
                        val colon = line.indexOf(':')
                        line.substring(0, colon.coerceAtLeast(0)).toIntOrNull()?.let { names[it] = line.substring(colon + 1) }
                    }
                }
            }
            return version?.takeIf { it.isNotEmpty() && names.isNotEmpty() }?.let { WebSymbolMap(it, names) }
        }
    }
}
