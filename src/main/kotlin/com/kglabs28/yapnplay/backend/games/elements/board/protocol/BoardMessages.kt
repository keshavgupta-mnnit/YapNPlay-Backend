package com.kglabs28.yapnplay.backend.games.elements.board.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire messages for board games with dice and pieces
 * (Royal Ludo, Monopoly-style games). Card and drawing games
 * define their own messages and never see these.
 */

// ── Match channel: client to server ───────────────────────────────────────

/** Roll the dice. */
@Serializable
data class RollDice(
    val matchId: String,
)

/** Move a piece. */
@Serializable
data class MovePiece(
    val matchId: String,
    val pieceId: String,
)

// ── Shared types ──────────────────────────────────────────────────────────

/** A legal move the acting seat can make. */
@Serializable
sealed interface LegalMove {
    @Serializable
    @SerialName("RollDice")
    data object RollDice : LegalMove

    @Serializable
    @SerialName("MovePiece")
    data class MovePiece(val pieceId: String) : LegalMove
}
