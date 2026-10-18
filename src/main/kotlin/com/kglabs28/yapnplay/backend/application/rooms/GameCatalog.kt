package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.games.base.GameRegistry
import com.kglabs28.yapnplay.backend.games.base.rules.GameDescriptor

/**
 * The games this server offers: the shared [GameRegistry] minus the ones
 * switched off with DISABLED_GAMES (the app's `game_enabled_<id>` flags).
 */
class GameCatalog(
    private val registry: GameRegistry,
    private val disabled: Set<String>,
) {
    /** The game, or null when it is unknown or switched off. */
    fun findEnabled(gameId: String): GameDescriptor? =
        registry.find(gameId)?.takeIf { gameId !in disabled }

    fun defaultHouseRules(gameId: String?): Map<String, Boolean> =
        gameId?.let(::findEnabled)?.houseRuleOptions?.associate { it.key to it.defaultEnabled }.orEmpty()
}
