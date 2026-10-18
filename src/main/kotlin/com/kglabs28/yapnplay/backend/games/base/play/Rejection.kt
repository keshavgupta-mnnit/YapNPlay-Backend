package com.kglabs28.yapnplay.backend.games.base.play

/**
 * A typed rejection from the game engine.
 * The server maps [code] to a protocol ErrorCode and sends a Rejected message.
 */
interface Rejection {
    val code: String
    val message: String
}

/**
 * A simple rejection implementation.
 */
data class TypedRejection(
    override val code: String,
    override val message: String,
) : Rejection
