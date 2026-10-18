package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.contract.ActiveMatch

/** The unfinished match [userId] can go back to, or null (none, or they left it). */
class GetActiveMatchUseCase(private val registry: MatchRegistry) {
    operator fun invoke(userId: String): ActiveMatch? =
        registry.activeMatchOf(userId)?.let { ActiveMatch(it.matchId, it.gameId, it.title, it.roomId) }
}
