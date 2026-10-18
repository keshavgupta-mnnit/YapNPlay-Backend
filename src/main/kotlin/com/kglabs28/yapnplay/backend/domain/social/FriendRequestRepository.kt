package com.kglabs28.yapnplay.backend.domain.social

/** Pending friend requests. */
interface FriendRequestRepository {
    suspend fun create(request: FriendRequestRecord)
    suspend fun find(id: String): FriendRequestRecord?
    /** Requests sent to [userId], newest first. */
    suspend fun incoming(userId: String): List<FriendRequestRecord>
    suspend fun between(fromId: String, toId: String): FriendRequestRecord?
    suspend fun delete(id: String)
    /** Removes requests in both directions between two people. */
    suspend fun deleteBetween(a: String, b: String)
}
