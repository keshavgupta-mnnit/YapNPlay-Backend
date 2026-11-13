package com.kglabs28.yapnplay.backend.domain.voice

/** One day's relay use: people who went through it, and bytes in all. */
data class RelayDay(val day: String, val users: Int, val bytes: Long)

/** Voice relay (TURN) bytes per calendar month ("YYYY-MM", UTC) and per person per day. */
interface RelayUsageStore {
    suspend fun add(month: String, bytes: Long, at: Long)

    suspend fun bytes(month: String): Long

    /** Adds [bytes] to [userId]'s relay use on [day] ("YYYY-MM-DD"). */
    suspend fun addDaily(day: String, userId: String, bytes: Long)

    /** Days in [fromDay, toDay] (both "YYYY-MM-DD", included) with relay use. */
    suspend fun daily(fromDay: String, toDay: String): List<RelayDay>

    /** Different people who used the relay in [fromDay, toDay]. */
    suspend fun users(fromDay: String, toDay: String): Int
}
