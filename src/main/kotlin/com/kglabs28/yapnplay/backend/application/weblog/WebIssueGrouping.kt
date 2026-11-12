package com.kglabs28.yapnplay.backend.application.weblog

import java.security.MessageDigest

/**
 * How web errors group into issues, like Crashlytics: by kind and the error itself (its text, before
 * the browser's " @ " trail of function numbers, which change with every build), so the same bug in
 * two builds is one issue.
 */
object WebIssueGrouping {
    /** The error kinds that make issues (the others are load and speed reports). */
    val ISSUE_KINDS = setOf("CRASH", "FAILED", "NON_FATAL")

    fun title(message: String): String = message.substringBefore(TRAIL).trim().ifEmpty { "Unknown error" }

    fun fingerprint(kind: String, message: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$kind|${title(message)}".toByteArray())
        return digest.take(FINGERPRINT_BYTES).joinToString("") { "%02x".format(it) }
    }

    /**
     * Where it broke: the first frame of a decoded stack in our own code, else the first that isn't
     * Kotlin's own (creating the error, throwing it). Empty without a decoded stack.
     */
    fun where(decodedStack: String): String {
        val frames = decodedStack.lines().map { it.substringBefore("  (").trim() }.filter { it.isNotEmpty() }
        return frames.firstOrNull { OUR_CODE in it }
            ?: frames.firstOrNull { frame -> RUNTIME.none { frame.startsWith(it) } }
            ?: ""
    }

    private const val TRAIL = " @ "
    private const val FINGERPRINT_BYTES = 8
    private const val OUR_CODE = "com.kglabs28"
    private val RUNTIME = listOf("kotlin.", "<", "Error", "RuntimeException")
}
