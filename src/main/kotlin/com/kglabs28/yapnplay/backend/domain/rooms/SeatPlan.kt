package com.kglabs28.yapnplay.backend.domain.rooms

/** Who plays when a room countdown ends. */
data class SeatPlan(
    val gameId: String,
    /** Humans who get a seat, in seat order. */
    val players: List<String>,
    /** CPU seats added by bot fill: up to the game's full room table (GameDescriptor.roomTableSize). */
    val botCount: Int,
    /** Present members without a seat. */
    val spectators: List<String>,
    val houseRules: Map<String, Boolean>,
    /** The room's league: each player's entry (coins). */
    val entryCoins: Int = 0,
)
