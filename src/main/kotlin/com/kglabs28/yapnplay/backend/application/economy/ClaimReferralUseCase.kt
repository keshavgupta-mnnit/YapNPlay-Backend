package com.kglabs28.yapnplay.backend.application.economy

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.ReferralClaimed
import com.kglabs28.yapnplay.backend.domain.common.Clock
import com.kglabs28.yapnplay.backend.domain.economy.CoinStore
import com.kglabs28.yapnplay.backend.domain.identity.User
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository
import com.kglabs28.yapnplay.backend.domain.settings.GameSettings
import com.kglabs28.yapnplay.backend.domain.settings.Settings

/**
 * The invite bonus: a new player who arrived through a friend's link (a room
 * invite or a shared YapNPlay ID) claims it once, and both get the admin's
 * `coins.referral_bonus`. Only for genuinely new players: the account is at
 * most [BackendConstants.REFERRAL_WINDOW_MS] old, younger than the friend's,
 * and the only account ever made on its phone (so reinstalling or making
 * extra accounts on one phone pays nothing). Never an error: a refused claim
 * answers `credited = false`.
 */
class ClaimReferralUseCase(
    private val users: UserRepository,
    private val coins: CoinStore,
    private val settings: Settings,
    private val clock: Clock,
) {
    suspend operator fun invoke(userId: String, referrerCode: String): ReferralClaimed {
        val now = clock.nowMillis()
        val bonus = settings[GameSettings.REFERRAL_BONUS]
        val inviter = inviterFor(userId, referrerCode, now)
        val paid = bonus > 0 && inviter != null && coins.payReferral(userId, inviter.id, bonus, now)
        return ReferralClaimed(credited = paid, bonus = if (paid) bonus else 0, coins = coins.balance(userId))
    }

    /** The friend behind [referrerCode], when [userId] is a newcomer they brought; otherwise null. */
    private suspend fun inviterFor(userId: String, referrerCode: String, now: Long): User? {
        val code = referrerCode.trim().uppercase()
        if (code.isEmpty() || code.length > BackendConstants.LINK_CODE_MAX_LENGTH) return null
        val user = users.findById(userId) ?: return null
        val inviter = users.findByCode(code) ?: return null
        if (inviter.id == user.id || inviter.createdAt > user.createdAt) return null
        if (now - user.createdAt > BackendConstants.REFERRAL_WINDOW_MS) return null
        if (!isOnlyAccountOnItsPhone(user.id)) return null
        return inviter
    }

    private suspend fun isOnlyAccountOnItsPhone(userId: String): Boolean {
        val device = users.deviceIdOf(userId) ?: return true
        return users.countByDeviceId(device) <= 1
    }
}
