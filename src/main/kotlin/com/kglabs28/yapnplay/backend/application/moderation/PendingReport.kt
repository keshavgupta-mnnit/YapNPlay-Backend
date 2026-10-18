package com.kglabs28.yapnplay.backend.application.moderation

/** A report waiting for votes until [expiresAt]; only each voter's first answer counts. */
class PendingReport(
    val id: String,
    val reporterId: String,
    val reportedId: String,
    val reportedName: String,
    val voters: Set<String>,
    val required: Int,
    val expiresAt: Long,
) {
    /** Voter → (agree, answered at). */
    val answers = mutableMapOf<String, Pair<Boolean, Long>>()
}
