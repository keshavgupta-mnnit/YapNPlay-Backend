package com.kglabs28.yapnplay.backend.domain.identity

import com.kglabs28.yapnplay.backend.config.BackendConstants

/**
 * XP and level. The real formula is not designed yet (contract: "e.g.
 * points by finishing place"); this placeholder gives 100 XP for 1st,
 * 20 less per place after (at least 10), and a level every 500 XP.
 */
class ProgressionPolicy {

    fun xpForPlace(place: Int): Int =
        (BackendConstants.XP_FIRST_PLACE - (place - 1) * BackendConstants.XP_STEP_PER_PLACE).coerceAtLeast(BackendConstants.XP_MINIMUM)

    fun levelFor(xp: Int): Int = 1 + xp.coerceAtLeast(0) / BackendConstants.XP_PER_LEVEL
}
