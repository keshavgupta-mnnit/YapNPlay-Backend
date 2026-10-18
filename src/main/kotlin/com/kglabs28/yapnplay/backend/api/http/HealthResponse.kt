package com.kglabs28.yapnplay.backend.api.http

import kotlinx.serialization.Serializable

/** `GET /health`. */
@Serializable
data class HealthResponse(
    val status: String,
    val contractVersion: String,
)
