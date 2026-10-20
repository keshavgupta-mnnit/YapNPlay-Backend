package com.kglabs28.yapnplay.backend.domain.identity

/** Turns a Play Games server auth code from the app into the player's stable Play Games id. */
interface PlayGamesVerifier {
    /** False when the server has no Play Games client secret configured. */
    val available: Boolean

    /** The player's id, or null when Google rejects the code. */
    suspend fun playerIdFor(serverAuthCode: String): String?
}
