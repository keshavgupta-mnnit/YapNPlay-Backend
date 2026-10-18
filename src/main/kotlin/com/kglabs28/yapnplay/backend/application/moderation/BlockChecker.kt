package com.kglabs28.yapnplay.backend.application.moderation

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.moderation.ModerationRepository
import com.kglabs28.yapnplay.backend.domain.moderation.Penalty

/** "New matches are refused until blockedUntil" (contract, moderation). */
class BlockChecker(
    private val moderation: ModerationRepository,
    private val clock: Clock,
) {
    suspend fun activeBlock(userId: String): Penalty? = moderation.activeBlock(userId, clock.nowMillis())

    suspend fun requireNotBlocked(userId: String) {
        if (activeBlock(userId) != null) throw ServiceException(ErrorCode.BLOCKED, BackendStrings.BLOCKED)
    }
}
