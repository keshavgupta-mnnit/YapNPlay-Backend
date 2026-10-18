package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/** `GET /me`: profile, XP/level and server-counted stats. Coins and gems stay 0 until the economy is designed. */
class GetMeUseCase(
    private val users: UserRepository,
    private val progression: ProgressionPolicy,
    private val codes: UserCodeAssigner,
) {
    suspend operator fun invoke(userId: String): Me {
        val found = users.findById(userId)
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        val user = codes.ensure(found)
        val stats = users.statsOf(user.id)
        return Me(
            userId = user.id,
            displayName = user.displayName,
            xp = user.xp,
            level = progression.levelFor(user.xp),
            username = user.username,
            userCode = user.userCode.orEmpty(),
            avatarId = user.avatarId,
            gamesPlayed = stats.played,
            gamesWon = stats.won,
        )
    }
}
