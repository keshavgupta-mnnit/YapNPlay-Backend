package com.kglabs28.yapnplay.backend.infrastructure.monitoring

import io.sentry.Breadcrumb
import io.sentry.SentryEvent

/**
 * Keeps secrets out of Sentry: sign-in headers and cookies are dropped,
 * and any `Bearer …` value or `token=` / `secret=` / `password=` value in
 * URLs, messages or log lines is replaced. Players appear only as user
 * ids in the messages, never with their secret.
 */
object SentryScrubber {

    fun scrub(event: SentryEvent): SentryEvent {
        event.request?.let { request ->
            request.headers = request.headers?.filterKeys { it.lowercase() !in SECRET_HEADERS }
            request.cookies = null
            request.queryString = request.queryString?.let(::clean)
            request.url = request.url?.let(::clean)
        }
        event.message?.let { message ->
            message.message = message.message?.let(::clean)
            message.formatted = message.formatted?.let(::clean)
        }
        event.exceptions?.forEach { it.value = it.value?.let(::clean) }
        event.user?.ipAddress = null
        return event
    }

    fun scrub(breadcrumb: Breadcrumb): Breadcrumb {
        breadcrumb.message = breadcrumb.message?.let(::clean)
        return breadcrumb
    }

    /** [text] with every bearer token and secret-looking parameter value removed. */
    fun clean(text: String): String =
        SECRET_PARAM.replace(BEARER.replace(text, BEARER_REPLACEMENT)) { it.groupValues[1] + "=" + REMOVED }

    private val SECRET_HEADERS = setOf("authorization", "cookie", "set-cookie", "sec-websocket-protocol")
    private val BEARER = Regex("(?i)bearer\\s+[^\\s\"',;]+")
    private val SECRET_PARAM = Regex("(?i)(token|secret|password|dsn)=[^&\\s\"',;]+")
    private const val REMOVED = "[removed]"
    private const val BEARER_REPLACEMENT = "Bearer $REMOVED"
}
