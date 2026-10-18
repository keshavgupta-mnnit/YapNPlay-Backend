package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.RandomSource

/** Names a new guest "Player-4821". The user can rename later. */
class GuestNameGenerator(private val random: RandomSource) {

    fun newName(): String =
        BackendConstants.GUEST_NAME_PREFIX +
            random.nextInt(BackendConstants.GUEST_NAME_NUMBER_MIN, BackendConstants.GUEST_NAME_NUMBER_MAX + 1)
}
