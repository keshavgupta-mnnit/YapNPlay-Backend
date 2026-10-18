package com.kglabs28.yapnplay.backend.domain.moderation

/** An incident against [userId]: [points] taken, new matches refused until [blockedUntil] (epoch millis). */
data class Penalty(
    val id: String,
    val userId: String,
    val reportId: String?,
    val points: Int,
    val blockedUntil: Long,
    val createdAt: Long,
)
