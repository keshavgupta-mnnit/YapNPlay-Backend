package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.contract.MatchHistoryItem
import com.kglabs28.yapnplay.backend.contract.MatchHistoryResponse
import com.kglabs28.yapnplay.backend.contract.MatchHistorySeat
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository

/** The caller's latest finished matches, newest first, with every seat's place, coins and XP. */
class GetMatchHistoryUseCase(private val results: MatchResultRepository) {
    suspend operator fun invoke(userId: String): MatchHistoryResponse =
        MatchHistoryResponse(
            results.history(userId, HISTORY_LIMIT).map { match ->
                MatchHistoryItem(
                    matchId = match.matchId,
                    gameId = match.gameId,
                    roomMatch = match.roomMatch,
                    startedAt = match.startedAt,
                    endedAt = match.endedAt,
                    mySeat = match.seats.firstOrNull { it.userId == userId }?.seat ?: 0,
                    seats = match.seats.map {
                        MatchHistorySeat(it.seat, it.userId, it.displayName, it.avatarId, it.place, it.coinsWon, it.completionCoins, it.stake, it.xp)
                    },
                )
            },
        )

    private companion object {
        /** How many past matches the history holds. */
        const val HISTORY_LIMIT = 50
    }
}
