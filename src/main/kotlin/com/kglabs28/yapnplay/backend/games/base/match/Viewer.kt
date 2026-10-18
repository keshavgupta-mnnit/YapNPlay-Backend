package com.kglabs28.yapnplay.backend.games.base.match

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber

/**
 * Who is viewing the match: a player with a seat, or a spectator.
 * Spectators are room members without a seat (not ready, beyond
 * maxPlayers, removed, or arrived mid-match). They see and hear the table
 * but never act and never count toward seats, minPlayers or bot fill.
 */
sealed interface Viewer {
    data class Player(val seat: SeatNumber) : Viewer
    data object Spectator : Viewer
}
