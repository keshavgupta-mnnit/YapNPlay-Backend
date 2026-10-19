package com.kglabs28.yapnplay.backend.config

/**
 * Which build is running. [version] is the git tag that was deployed: the
 * deploy workflow sets APP_VERSION on Render from the tag (v1.0.0 → 1.0.0).
 * [commit] is the commit Render built (RENDER_GIT_COMMIT). Off Render both
 * read "local".
 */
object BuildInfo {
    val version: String by lazy { env(APP_VERSION_ENV) ?: LOCAL }

    val commit: String by lazy { env(RENDER_COMMIT_ENV)?.take(SHORT_SHA) ?: LOCAL }

    private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    private const val APP_VERSION_ENV = "APP_VERSION"
    private const val RENDER_COMMIT_ENV = "RENDER_GIT_COMMIT"
    private const val SHORT_SHA = 7
    private const val LOCAL = "local"
}
