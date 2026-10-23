package com.kglabs28.yapnplay.backend.application.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.contract.DeviceAccount
import com.kglabs28.yapnplay.backend.domain.identity.ProgressionPolicy
import com.kglabs28.yapnplay.backend.domain.identity.UserRepository

/**
 * `POST /auth/device`: the account already made on this phone, for the
 * app's "Continue as Keshav (Level 3) or start new?" (name, avatar and
 * level only; continuing goes through guest sign-up with restore).
 */
class FindDeviceAccountUseCase(
    private val users: UserRepository,
    private val progression: ProgressionPolicy,
) {
    suspend operator fun invoke(deviceId: String): DeviceAccount {
        val id = deviceId.trim().takeIf { BackendConstants.DEVICE_ID_PATTERN.matches(it) } ?: return DeviceAccount(found = false)
        val user = users.findByDeviceId(id) ?: return DeviceAccount(found = false)
        return DeviceAccount(found = true, displayName = user.displayName, avatarId = user.avatarId, level = progression.levelFor(user.xp))
    }
}
