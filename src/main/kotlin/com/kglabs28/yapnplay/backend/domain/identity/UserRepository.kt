package com.kglabs28.yapnplay.backend.domain.identity

/** Stores users. */
interface UserRepository {
    suspend fun insert(user: User)
    suspend fun findById(id: String): User?
    suspend fun updateDisplayName(id: String, displayName: String): Boolean

    /** Adds [delta] (may be negative) to the XP of user [id], never below zero. */
    suspend fun addXp(id: String, delta: Int)

    suspend fun findByCode(userCode: String): User?

    /** Changes only the fields that are not null. */
    suspend fun updateProfile(id: String, displayName: String?, username: String?, avatarId: String?)

    /** Gives the user [code] if they have none yet; false when the code is taken. */
    suspend fun assignCode(id: String, code: String): Boolean

    /** Matches played and won (1st place), from stored results. */
    suspend fun statsOf(id: String): GameStats
}
