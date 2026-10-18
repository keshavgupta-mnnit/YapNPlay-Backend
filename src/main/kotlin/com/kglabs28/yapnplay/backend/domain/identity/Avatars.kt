package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.RandomSource

/** Preset avatar ids (a01..a18): a random one for new guests, fixed ones for CPUs. */
object Avatars {

    /** The preset id for [number] (1-based), e.g. 9 → "a09". */
    fun id(number: Int): String =
        BackendConstants.AVATAR_PREFIX + number.toString().padStart(BackendConstants.AVATAR_DIGITS, '0')

    /** Any preset, for a new guest. */
    fun random(random: RandomSource): String = id(random.nextInt(1, BackendConstants.AVATAR_COUNT + 1))

    /** CPU seats take turns between the two robot avatars: CPU 1 → a09, CPU 2 → a18, CPU 3 → a09… */
    fun forCpu(cpuNumber: Int): String =
        BackendConstants.CPU_AVATARS[(cpuNumber - 1).mod(BackendConstants.CPU_AVATARS.size)]
}
