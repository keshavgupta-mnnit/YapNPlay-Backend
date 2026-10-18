package com.kglabs28.yapnplay.backend.domain.social

/** Mutual friendships: both directions are written and removed together. */
interface FriendRepository {
    suspend fun addMutual(a: String, b: String, at: Long)
    suspend fun removeMutual(a: String, b: String)
    suspend fun friendsOf(userId: String): Set<String>
}
