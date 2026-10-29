package com.kglabs28.yapnplay.backend.domain.rooms

import com.kglabs28.yapnplay.backend.games.base.rules.GameDescriptor
import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * When a room may start and who gets the seats (contract "Match start in
 * a room" + decisions.md "who plays"). The leader always counts as ready.
 */
class StartPolicy {

    /** Online humans who will play, leader first, then by ready time. */
    fun readyHumans(members: Collection<LiveMember>, leaderId: String): List<LiveMember> =
        members.filter { it.online && (it.ready || it.userId == leaderId) }
            .sortedWith(compareBy<LiveMember>({ it.userId != leaderId }, { it.readyAt ?: Long.MAX_VALUE }))

    fun canStart(members: Collection<LiveMember>, leaderId: String, game: GameDescriptor, botFill: Boolean): Boolean {
        val ready = readyHumans(members, leaderId).size
        return ready >= BackendConstants.MIN_HUMANS_TO_START && (ready >= game.minPlayers || botFill)
    }

    fun plan(
        members: Collection<LiveMember>,
        leaderId: String,
        game: GameDescriptor,
        botFill: Boolean,
        houseRules: Map<String, Boolean>,
        entryCoins: Int = 0,
    ): SeatPlan {
        val players = readyHumans(members, leaderId).take(game.maxPlayers).map { it.userId }
        val bots = if (botFill && players.size < game.minPlayers) game.minPlayers - players.size else 0
        val spectators = members.filter { it.online && it.userId !in players }.map { it.userId }
        return SeatPlan(game.id.value, players, bots, spectators, houseRules, entryCoins)
    }
}
