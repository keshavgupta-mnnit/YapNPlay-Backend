package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * Profile rules: avatars are presets "a01", "a02", ... never photos.
 */
class ProfilePolicy {
    fun isValidAvatar(avatarId: String): Boolean {
        if (!avatarId.startsWith(BackendConstants.AVATAR_PREFIX)) return false
        val number = avatarId.removePrefix(BackendConstants.AVATAR_PREFIX)
        return number.length == BackendConstants.AVATAR_DIGITS && (number.toIntOrNull() ?: 0) in 1..BackendConstants.AVATAR_COUNT
    }
}
