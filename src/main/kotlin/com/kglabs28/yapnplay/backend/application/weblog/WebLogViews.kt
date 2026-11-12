package com.kglabs28.yapnplay.backend.application.weblog

import kotlinx.serialization.Serializable

/**
 * What a browser sends (all optional: a broken page sends what it can). Kind REPEATS isn't an event:
 * the event [eventId] happened [occurrences] times in its page load.
 */
@Serializable
data class WebLogBody(
    val kind: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val userAgent: String? = null,
    val device: String? = null,
    val elapsedMs: Long? = null,
    val version: String? = null,
    val stack: String? = null,
    val eventId: String? = null,
    val install: String? = null,
    val pageLoad: String? = null,
    val occurrences: Int? = null,
    val context: Map<String, String>? = null,
    val breadcrumbs: List<String>? = null,
)

/** A report as the admin dashboard shows it ([os] and [browser] read from [userAgent]). */
@Serializable
data class WebLogView(
    val id: String,
    val createdAt: Long,
    val kind: String,
    val stage: String,
    val message: String,
    val userAgent: String,
    val device: String,
    val elapsedMs: Long,
    val version: String,
    val stack: String = "",
    /** [stack] with Kotlin function names (empty when it couldn't be decoded: see [decodeNote]). */
    val decodedStack: String = "",
    val decodeNote: String = "",
    val installId: String = "",
    val pageLoad: String = "",
    val occurrences: Int = 1,
    val context: Map<String, String> = emptyMap(),
    val breadcrumbs: List<String> = emptyList(),
    val os: String = "",
    val browser: String = "",
)

/** How many reports a clear removed. */
@Serializable
data class WebLogsRemoved(val removed: Int)
