package com.kglabs28.yapnplay.backend.application.rooms

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.RoomLeaderboard
import com.kglabs28.yapnplay.backend.contract.RoomStanding
import com.kglabs28.yapnplay.backend.domain.match.MatchResultRepository
import com.kglabs28.yapnplay.backend.domain.rooms.PlayRoomRepository

/** `GET /rooms/{roomId}/leaderboard`: every member's record in the room (members only); someone with no match yet shows zeros. */
class GetRoomLeaderboardUseCase(
    private val rooms: PlayRoomRepository,
    private val results: MatchResultRepository,
) {
    suspend operator fun invoke(userId: String, roomId: String): RoomLeaderboard {
        val members = rooms.members(roomId)
        if (members.none { it.userId == userId }) throw ServiceException(ErrorCode.NOT_MEMBER, BackendStrings.NOT_MEMBER)
        val totals = results.roomTotals(roomId)
        return RoomLeaderboard(
            members.map { m ->
                val t = totals[m.userId]
                RoomStanding(m.userId, m.displayName, m.avatarId, t?.played ?: 0, t?.wins ?: 0, t?.coins ?: 0)
            },
        )
    }
}
