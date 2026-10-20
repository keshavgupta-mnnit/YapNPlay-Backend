package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** The players voted to keep going at the limit: add [extraRounds] / [extraMinutes] and play on (server). */
data class ExtendMatch(val extraRounds: Int, val extraMinutes: Int) : Action

/** The time limit is up at a turn boundary: wait for the keep-playing vote (server). */
data object ReachTimeLimit : Action
