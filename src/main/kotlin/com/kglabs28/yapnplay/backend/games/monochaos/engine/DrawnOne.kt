package com.kglabs28.yapnplay.backend.games.monochaos.engine

import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosCard
import com.kglabs28.yapnplay.backend.games.monochaos.domain.MonoChaosState

/** One card taken from the deck ([card], null when none was left) and the table after it ([state]). */
internal data class DrawnOne(val state: MonoChaosState, val card: MonoChaosCard?)
