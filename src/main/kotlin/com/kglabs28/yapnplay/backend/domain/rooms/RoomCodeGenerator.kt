package com.kglabs28.yapnplay.backend.domain.rooms

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.RandomSource

/** Random 6-digit room codes (decisions.md: digits only for now). */
class RoomCodeGenerator(private val random: RandomSource) {

    fun newCode(): String = buildString {
        repeat(BackendConstants.ROOM_CODE_LENGTH) {
            append(BackendConstants.ROOM_CODE_ALPHABET[random.nextInt(0, BackendConstants.ROOM_CODE_ALPHABET.length)])
        }
    }

    fun isValid(code: String): Boolean =
        code.length == BackendConstants.ROOM_CODE_LENGTH && code.all { it in BackendConstants.ROOM_CODE_ALPHABET }
}
