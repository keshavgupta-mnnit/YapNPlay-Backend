package com.kglabs28.yapnplay.backend.domain.identity

/** Creates the long random secret handed to a new guest exactly once. */
fun interface SecretGenerator {
    fun newSecret(): String
}
