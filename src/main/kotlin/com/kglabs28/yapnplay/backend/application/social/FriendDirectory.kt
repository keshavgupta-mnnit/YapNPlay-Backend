package com.kglabs28.yapnplay.backend.application.social

import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.social.FriendRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Each user's friends, cached in memory because every room snapshot asks
 * (`RoomMember.isFriend` is relative to the receiver). Friendship is
 * mutual, so both people's caches change together.
 */
class FriendDirectory(
    private val friends: FriendRepository,
    private val clock: Clock,
) {
    private val cache = ConcurrentHashMap<String, Set<String>>()

    suspend fun friendsOf(userId: String): Set<String> =
        cache[userId] ?: friends.friendsOf(userId).also { cache[userId] = it }

    suspend fun befriend(a: String, b: String) {
        friends.addMutual(a, b, clock.nowMillis())
        cache[a] = friendsOf(a) + b
        cache[b] = friendsOf(b) + a
    }

    suspend fun unfriend(a: String, b: String) {
        friends.removeMutual(a, b)
        cache[a] = friendsOf(a) - b
        cache[b] = friendsOf(b) - a
    }
}
