package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.social.FriendRepository

/** Mutual friendships in memory. */
class InMemoryFriendRepository : FriendRepository {
    private val friends = mutableMapOf<String, MutableSet<String>>()

    override suspend fun addMutual(a: String, b: String, at: Long) {
        friends.getOrPut(a) { mutableSetOf() } += b
        friends.getOrPut(b) { mutableSetOf() } += a
    }

    override suspend fun removeMutual(a: String, b: String) {
        friends[a]?.remove(b)
        friends[b]?.remove(a)
    }

    override suspend fun friendsOf(userId: String): Set<String> = friends[userId].orEmpty()
}
