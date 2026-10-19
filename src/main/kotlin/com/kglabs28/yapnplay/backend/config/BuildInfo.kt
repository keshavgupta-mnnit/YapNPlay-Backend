package com.kglabs28.yapnplay.backend.config

import java.util.Properties

/**
 * Which build is running: [version] is the Gradle `version` baked in at
 * build time (the deploy workflow makes the git tag match it), [commit] the
 * git commit Render built (RENDER_GIT_COMMIT), or "local" off Render.
 */
object BuildInfo {
    val version: String by lazy {
        runCatching {
            BuildInfo::class.java.getResourceAsStream(RESOURCE)?.use { stream ->
                Properties().apply { load(stream) }.getProperty(VERSION_KEY)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN
    }

    val commit: String by lazy {
        System.getenv(RENDER_COMMIT_ENV)?.take(SHORT_SHA)?.takeIf { it.isNotBlank() } ?: LOCAL
    }

    private const val RESOURCE = "/build-info.properties"
    private const val VERSION_KEY = "version"
    private const val RENDER_COMMIT_ENV = "RENDER_GIT_COMMIT"
    private const val SHORT_SHA = 7
    private const val UNKNOWN = "unknown"
    private const val LOCAL = "local"
}
