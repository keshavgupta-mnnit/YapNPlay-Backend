package com.kglabs28.yapnplay.backend.games.base

import com.kglabs28.yapnplay.backend.games.base.rules.GameDescriptor

/**
 * Every game built into this binary. Each game registers itself once
 * (one line in the games Koin module); pickers, matchmaking and the
 * server's queues all read from here, so a new game needs no screen
 * changes. The server builds the same registry from the same game code.
 */
class GameRegistry(val games: List<GameDescriptor>) {

    fun find(gameId: String): GameDescriptor? = games.firstOrNull { it.id.value == gameId }
}
