package com.kglabs28.yapnplay.backend.domain.rooms

/** The admins' Waiting Lists. */
interface JoinRequestRepository {
    /** Adds the request (no-op when it already exists). */
    suspend fun add(roomId: String, userId: String, at: Long)
    suspend fun remove(roomId: String, userId: String)
    suspend fun listFor(roomId: String): List<JoinRequest>
}
