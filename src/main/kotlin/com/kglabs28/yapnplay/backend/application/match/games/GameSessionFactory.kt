package com.kglabs28.yapnplay.backend.application.match.games

import com.kglabs28.yapnplay.backend.games.base.Game

/** Starts sessions of one game. One factory per game the server offers. */
interface GameSessionFactory {
    val game: Game

    /** Seats at a Quick Play table and the most a room table may have. */
    val quickPlaySeats: Int get() = game.matchmaking.seats

    /**
     * A new session for [seatCount] players. [houseRules] null means a
     * public match: the game's traditional rules, nothing changed.
     */
    fun start(seatCount: Int, houseRules: Map<String, Boolean>?): GameSession
}
