package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.application.economy.CoinsService
import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.Me
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/** `GET /me`: profile, XP/level, coins (the day's bonus is added here, once a day) and server-counted stats. */
class GetMeUseCase(
    private val users: UserRepository,
    private val progression: ProgressionPolicy,
    private val codes: UserCodeAssigner,
    private val coins: CoinsService? = null,
) {
    suspend operator fun invoke(userId: String): Me {
        val found = users.findById(userId)
            ?: throw ServiceException(ErrorCode.NOT_AUTHENTICATED, BackendStrings.NOT_AUTHENTICATED)
        val user = codes.ensure(found)
        val stats = users.statsOf(user.id)
        val bonus = coins?.dailyBonus(user.id) ?: 0
        val level = progression.levelFor(user.xp)
        return Me(
            userId = user.id,
            displayName = user.displayName,
            xp = user.xp,
            level = level,
            levelXp = progression.levelStartXp(level),
            nextLevelXp = progression.levelStartXp(level + 1),
            coins = coins?.balance(user.id) ?: 0,
            dailyBonus = bonus,
            username = user.username,
            userCode = user.userCode.orEmpty(),
            avatarId = user.avatarId,
            gamesPlayed = stats.played,
            gamesWon = stats.won,
        )
    }
}
