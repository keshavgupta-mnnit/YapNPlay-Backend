package com.kglabs28.yapnplay.backend.infrastructure.time

import com.kglabs28.yapnplay.backend.domain.common.Clock

/** The real wall clock. */
class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
