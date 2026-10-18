package com.kglabs28.yapnplay.backend.infrastructure.security

import com.kglabs28.yapnplay.backend.domain.common.RandomSource
import java.security.SecureRandom

/** [RandomSource] backed by the operating system's secure generator. */
class SecureRandomSource(private val random: SecureRandom = SecureRandom()) : RandomSource {
    override fun nextInt(from: Int, untilExclusive: Int): Int = from + random.nextInt(untilExclusive - from)
}
