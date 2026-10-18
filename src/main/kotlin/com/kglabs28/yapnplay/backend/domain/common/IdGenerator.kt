package com.kglabs28.yapnplay.backend.domain.common

/** Creates unique ids for users, rooms and matches. Injected so tests can predict them. */
fun interface IdGenerator {
    fun newId(): String
}
