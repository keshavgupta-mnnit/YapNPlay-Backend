package com.kglabs28.yapnplay.backend.application.match.actor

import com.kglabs28.yapnplay.backend.contract.ErrorCode
import com.kglabs28.yapnplay.backend.contract.MatchSnapshot

/** What a match answers. Small hierarchy, kept together. */
sealed interface MatchResult {
    data object Done : MatchResult
    data class Snapshot(val snapshot: MatchSnapshot) : MatchResult
    data class Failed(val code: ErrorCode, val message: String) : MatchResult
}
