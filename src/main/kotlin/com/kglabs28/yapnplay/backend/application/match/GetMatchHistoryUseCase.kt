package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.contract.MatchHistoryItem
import com.kglabs28.yapnplay.backend.contract.MatchHistoryResponse
import com.kglabs28.yapnplay.backend.contract.MatchHistorySeat
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository

/**
 * The caller's finished matches, newest first, a page of [PAGE_SIZE] at a time ([page] from 0),
 * with every seat's place, coins and XP; [MatchHistoryResponse.hasMore] says another page follows.
 */
class GetMatchHistoryUseCase(private val results: MatchResultRepository) {
    suspend operator fun invoke(userId: String, page: Int = 0): MatchHistoryResponse {
        // One more than a page, to know whether there is a next one.
        val found = results.history(userId, PAGE_SIZE + 1, page.coerceAtLeast(0).toLong() * PAGE_SIZE)
        return MatchHistoryResponse(
            hasMore = found.size > PAGE_SIZE,
            matches = found.take(PAGE_SIZE).map { match ->
                MatchHistoryItem(
                    matchId = match.matchId,
                    gameId = match.gameId,
                    roomMatch = match.roomMatch,
                    roomName = match.roomName,
                    startedAt = match.startedAt,
                    endedAt = match.endedAt,
                    // Two seats in one match (left, then sat down again): the one they finished in.
                    mySeat = match.seats.filter { it.userId == userId }.minByOrNull { if (it.left) 1 else 0 }?.seat ?: 0,
                    seats = match.seats.map {
                        MatchHistorySeat(it.seat, it.userId, it.displayName, it.avatarId, it.place, it.coinsWon, it.completionCoins, it.stake, it.xp, left = it.left)
                    },
                )
            },
        )
    }

    private companion object {
        /** Matches a page. */
        const val PAGE_SIZE = 20
    }
}
