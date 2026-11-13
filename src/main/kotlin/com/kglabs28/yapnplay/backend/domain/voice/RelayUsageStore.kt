package com.kglabs28.yapnplay.backend.domain.voice

/** Voice relay (TURN) bytes per calendar month ("YYYY-MM", UTC). */
interface RelayUsageStore {
    suspend fun add(month: String, bytes: Long, at: Long)

    suspend fun bytes(month: String): Long
}
