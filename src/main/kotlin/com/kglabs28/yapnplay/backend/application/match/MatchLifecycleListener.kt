package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.domain.match.FinishedMatch

/** Told when a match ends (results, XP, the room lobby). */
fun interface MatchLifecycleListener {
    suspend fun onFinished(match: FinishedMatch)
}
