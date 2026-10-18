package com.kglabs28.yapnplay.backend.domain.social

/** A phone that can receive pushes for [userId]. */
data class Device(
    val userId: String,
    val token: String,
    val platform: String,
)
