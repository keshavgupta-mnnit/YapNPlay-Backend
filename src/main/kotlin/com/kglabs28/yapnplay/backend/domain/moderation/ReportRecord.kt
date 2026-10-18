package com.kglabs28.yapnplay.backend.domain.moderation

/** A stored report and, once decided, its outcome. */
data class ReportRecord(
    val id: String,
    val contextId: String,
    val reporterId: String,
    val reportedId: String,
    val reason: String,
    val required: Int,
    val createdAt: Long,
)
