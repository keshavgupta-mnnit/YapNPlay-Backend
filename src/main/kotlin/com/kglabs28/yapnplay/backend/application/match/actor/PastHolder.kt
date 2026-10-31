package com.kglabs28.yapnplay.backend.application.match.actor

/**
 * Someone whose seat another person took over mid-match (they left, or
 * were away and the seat was handed on). They still get a result, ranked
 * below everyone at the table, the latest to lose their seat first.
 */
data class PastHolder(
    val seat: Int,
    val userId: String,
    val displayName: String,
    val avatarId: String,
    /** When they lost the seat (epoch millis). */
    val leftAt: Long,
)
