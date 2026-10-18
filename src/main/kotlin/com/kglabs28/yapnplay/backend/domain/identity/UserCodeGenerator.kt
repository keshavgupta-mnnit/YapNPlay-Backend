package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.RandomSource

/** The 6-character friend ID, with no look-alike characters (no 0/O, 1/I). */
class UserCodeGenerator(private val random: RandomSource) {

    fun newCode(): String = buildString {
        repeat(BackendConstants.USER_CODE_LENGTH) {
            append(BackendConstants.USER_CODE_ALPHABET[random.nextInt(0, BackendConstants.USER_CODE_ALPHABET.length)])
        }
    }

    fun normalize(raw: String): String = raw.trim().uppercase()
}
