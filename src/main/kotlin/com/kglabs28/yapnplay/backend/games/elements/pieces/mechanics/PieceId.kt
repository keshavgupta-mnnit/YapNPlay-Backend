package com.kglabs28.yapnplay.backend.games.elements.pieces.mechanics

import kotlinx.serialization.Serializable

/**
 * A piece identifier. Shared by piece games (Royal Ludo, Monopoly-style games).
 * Card and drawing games never see this type.
 */
@Serializable
@JvmInline
value class PieceId(val value: String) {
    init {
        require(value.isNotBlank()) { "PieceId must not be blank" }
    }
}
