package com.kglabs28.yapnplay.backend.support

import com.kglabs28.yapnplay.backend.domain.identity.GameStats
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/** Users in memory; any id asked for exists with its id as the name. */
class InMemoryUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()

    override suspend fun insert(user: User) {
        users[user.id] = user
    }

    override suspend fun findById(id: String): User? = users.getOrPut(id) { User(id, id, "", 0) }

    override suspend fun updateDisplayName(id: String, displayName: String): Boolean {
        users[id] = findById(id)!!.copy(displayName = displayName)
        return true
    }

    override suspend fun addXp(id: String, delta: Int) {
        val user = findById(id)!!
        users[id] = user.copy(xp = (user.xp + delta).coerceAtLeast(0))
    }

    override suspend fun findByCode(userCode: String): User? = users.values.firstOrNull { it.userCode == userCode }

    override suspend fun updateProfile(id: String, displayName: String?, username: String?, avatarId: String?) {
        val user = findById(id)!!
        users[id] = user.copy(
            displayName = displayName ?: user.displayName,
            username = username ?: user.username,
            avatarId = avatarId ?: user.avatarId,
        )
    }

    override suspend fun assignCode(id: String, code: String): Boolean {
        val user = findById(id)!!
        if (user.userCode != null || users.values.any { it.userCode == code }) return false
        users[id] = user.copy(userCode = code)
        return true
    }

    override suspend fun statsOf(id: String): GameStats = GameStats(0, 0)

    private val playGames = mutableMapOf<String, String>()

    override suspend fun findByPlayGamesId(playerId: String): User? =
        playGames.entries.firstOrNull { it.value == playerId }?.key?.let { users[it] }

    override suspend fun linkPlayGames(id: String, playerId: String): Boolean {
        if (id in playGames || playerId in playGames.values) return false
        playGames[id] = playerId
        return true
    }

    override suspend fun updateSecretHash(id: String, secretHash: String) {
        users[id]?.let { users[id] = it.copy(secretHash = secretHash) }
    }
}
