package com.kglabs28.yapnplay.backend.domain.social

/** The user blocked [blockedId] at [blockedAt] (epoch millis). */
data class BlockRecord(
    val blockedId: String,
    val blockedAt: Long,
)
