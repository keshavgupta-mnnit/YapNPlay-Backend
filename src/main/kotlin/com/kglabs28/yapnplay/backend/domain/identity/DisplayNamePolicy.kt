package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * Display-name rules from the contract: trimmed, 1 to 24 characters, no
 * control characters, duplicates allowed.
 */
class DisplayNamePolicy {

    /** The cleaned name, or null when it breaks a rule. */
    fun normalize(raw: String): String? {
        val name = raw.trim()
        val valid = name.isNotEmpty() &&
            name.length <= BackendConstants.DISPLAY_NAME_MAX_LENGTH &&
            name.none { it.isISOControl() }
        return name.takeIf { valid }
    }
}
