package com.kglabs28.yapnplay.backend.api.http

import kotlinx.serialization.Serializable

/** `GET /health`. */
@Serializable
data class HealthResponse(
    val status: String,
    val contractVersion: String,
    /** The deployed release (from the git tag), e.g. 1.0.1; "local" off Render. */
    val version: String = "local",
)
