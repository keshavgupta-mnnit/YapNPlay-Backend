package com.kglabs28.yapnplay.backend.di

import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.infrastructure.db.DatabaseHandle

/**
 * Replacements tests hand to the server. A [database] passed in is owned
 * by the caller and is not closed when the server stops.
 */
data class BackendOverrides(
    val database: DatabaseHandle? = null,
    val clock: Clock? = null,
)
