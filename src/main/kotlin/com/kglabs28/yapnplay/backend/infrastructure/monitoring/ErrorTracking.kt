package com.kglabs28.yapnplay.backend.infrastructure.monitoring

import com.kglabs28.yapnplay.backend.config.BuildInfo
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.kglabs28.yapnplay.backend.config.BackendConfig
import com.kglabs28.yapnplay.backend.contract.transport.CONTRACT_VERSION
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.logback.SentryAppender
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Server error tracking with Sentry, on only when SENTRY_DSN is set.
 * Every line the server logs at ERROR (unhandled HTTP errors, failed match
 * and room actions, background work that crashed) becomes a Sentry issue,
 * with the INFO lines before it as breadcrumbs. Secrets are scrubbed first
 * ([SentryScrubber]). Without a DSN nothing changes: errors stay in the log.
 */
object ErrorTracking {

    /** True once Sentry is reporting (a DSN was given). */
    @Volatile
    var enabled: Boolean = false
        private set

    /** Starts reporting if [BackendConfig.sentryDsn] is set; returns whether it did. */
    fun start(config: BackendConfig): Boolean {
        val dsn = config.sentryDsn ?: return false
        Sentry.init { options ->
            options.dsn = dsn
            options.environment = if (config.devMode) ENV_LOCAL else ENV_PRODUCTION
            options.release = RELEASE_PREFIX + BuildInfo.version
            options.isSendDefaultPii = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> SentryScrubber.scrub(event) }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { crumb, _ -> SentryScrubber.scrub(crumb) }
        }
        attachToLogs()
        enabled = true
        LoggerFactory.getLogger(ErrorTracking::class.java).info("Server errors are reported to Sentry")
        return true
    }

    /** Sends what is queued (used after a test error so it shows up at once). */
    fun flush() {
        if (enabled) Sentry.flush(FLUSH_TIMEOUT_MS)
    }

    private fun attachToLogs() {
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
        val appender = SentryAppender().apply {
            this.context = context
            name = APPENDER_NAME
            setMinimumEventLevel(Level.ERROR)
            setMinimumBreadcrumbLevel(Level.INFO)
            start()
        }
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
    }

    private const val ENV_LOCAL = "local"
    private const val ENV_PRODUCTION = "production"
    private const val RELEASE_PREFIX = "yapnplay-backend@"
    private const val APPENDER_NAME = "SENTRY"
    private const val FLUSH_TIMEOUT_MS = 2_000L
}
