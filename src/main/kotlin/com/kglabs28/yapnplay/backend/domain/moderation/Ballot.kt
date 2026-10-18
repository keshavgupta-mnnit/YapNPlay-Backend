package com.kglabs28.yapnplay.backend.domain.moderation

/** One voter's answer on a report: AGREE, DISAGREE or NO_RESPONSE. */
data class Ballot(
    val reportId: String,
    val voterId: String,
    val response: String,
    val recordedAt: Long,
)
