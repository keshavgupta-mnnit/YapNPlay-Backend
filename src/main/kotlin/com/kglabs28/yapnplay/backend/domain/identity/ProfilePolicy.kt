package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * Profile rules (contract 0.3.1): a username is 3–20 of A–Z a–z 0–9 _ .
 * and not unique; avatars are presets "a01", "a02", ... never photos.
 */
class ProfilePolicy {
    private val usernamePattern = Regex(BackendConstants.USERNAME_PATTERN)

    fun normalizeUsername(raw: String): String? = raw.trim().takeIf { usernamePattern.matches(it) }

    fun isValidAvatar(avatarId: String): Boolean {
        if (!avatarId.startsWith(BackendConstants.AVATAR_PREFIX)) return false
        val number = avatarId.removePrefix(BackendConstants.AVATAR_PREFIX)
        return number.length == BackendConstants.AVATAR_DIGITS && (number.toIntOrNull() ?: 0) in 1..BackendConstants.AVATAR_COUNT
    }
}
