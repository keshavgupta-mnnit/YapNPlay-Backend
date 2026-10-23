package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants
import com.kglabs28.yapnplay.backend.domain.common.RandomSource

/** Preset avatar ids (a01..a18): a random one for new guests, fixed ones for CPUs. */
object Avatars {

    /** The preset id for [number] (1-based), e.g. 9 → "a09". */
    fun id(number: Int): String =
        BackendConstants.AVATAR_PREFIX + number.toString().padStart(BackendConstants.AVATAR_DIGITS, '0')

    /** The presets a person can be given (every one but the CPUs' robots). */
    val forPeople: List<String> = (1..BackendConstants.AVATAR_COUNT).map(::id) - BackendConstants.CPU_AVATARS.toSet()

    /** Any person preset, for a new guest (never a robot avatar). */
    fun random(random: RandomSource): String = forPeople[random.nextInt(0, forPeople.size)]

    /** Odd-numbered CPUs get one robot avatar, even-numbered the other: CPU 1 → a09, CPU 2 → a18, CPU 3 → a09… */
    fun forCpu(cpuNumber: Int): String =
        if (cpuNumber % 2 == 1) BackendConstants.CPU_AVATAR_ODD else BackendConstants.CPU_AVATAR_EVEN
}
