package com.kglabs28.yapnplay.backend.domain.identity

/** Stores users. */
interface UserRepository {
    suspend fun insert(user: User)
    suspend fun findById(id: String): User?
    suspend fun updateDisplayName(id: String, displayName: String): Boolean

    /** Adds [delta] (may be negative) to the XP of user [id], never below zero. */
    suspend fun addXp(id: String, delta: Int)

    suspend fun findByCode(userCode: String): User?

    /** The newest account made on the phone [deviceId], or null. */
    suspend fun findByDeviceId(deviceId: String): User?

    /** Records the phone the account was made on. */
    suspend fun setDeviceId(id: String, deviceId: String)

    /** Adds [millis] connected to the user's time in the app; [at] is when they left. */
    suspend fun addOnlineTime(id: String, millis: Long, at: Long)

    /** Changes only the fields that are not null. */
    suspend fun updateProfile(id: String, displayName: String?, avatarId: String?)

    /** Gives the user [code] if they have none yet; false when the code is taken. */
    suspend fun assignCode(id: String, code: String): Boolean

    /** The account linked to Play Games player [playerId], if any. */
    suspend fun findByPlayGamesId(playerId: String): User?

    /** Links [playerId] to user [id]; false when that user already has a link or the player is taken. */
    suspend fun linkPlayGames(id: String, playerId: String): Boolean

    /** Replaces the user's secret (only its hash is stored). */
    suspend fun updateSecretHash(id: String, secretHash: String)

    /** Matches played and won (1st place), from stored results. */
    suspend fun statsOf(id: String): GameStats
}
