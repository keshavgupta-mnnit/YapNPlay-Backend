package com.kglabs28.yapnplay.backend.domain.identity

/** A guest account. [secretHash] is the SHA-256 of the secret; the secret itself is never stored. */
data class User(
    val id: String,
    val displayName: String,
    val secretHash: String,
    val createdAt: Long,
    /** Experience from match results; level derives from it (ProgressionPolicy). */
    val xp: Int = 0,
    /** Shown as "@username"; not unique ("" until chosen). */
    val username: String = "",
    /** Unique 6-character ID friends type to add this user; null only for accounts made before profiles. */
    val userCode: String? = null,
    /** A preset avatar ("" = default). */
    val avatarId: String = "",
)
