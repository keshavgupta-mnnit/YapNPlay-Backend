package com.kglabs28.yapnplay.backend.domain.identity

/** Turns a Google ID token from the web app's sign-in into the Google account's stable subject id. */
interface GoogleIdVerifier {
    /** False when the server has no web OAuth client id configured. */
    val available: Boolean

    /** The account's subject id, or null when Google does not vouch for the token (or it is for another app). */
    suspend fun subjectFor(idToken: String): String?
}
