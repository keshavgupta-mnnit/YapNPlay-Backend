package com.kglabs28.yapnplay.backend.application.match

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.actor.MatchAction
import com.kglabs28.yapnplay.backend.application.match.actor.MatchResult
import com.kglabs28.yapnplay.backend.config.BackendStrings
import com.kglabs28.yapnplay.backend.contract.ErrorCode

/** Sends a MATCH-channel request to its match; unknown match ids are `NOT_FOUND`. */
class SendMatchActionUseCase(private val matches: MatchService) {

    suspend operator fun invoke(userId: String, matchId: String, action: MatchAction): MatchResult {
        val actor = matches.find(matchId) ?: throw ServiceException(ErrorCode.NOT_FOUND, BackendStrings.NO_SUCH_MATCH)
        val result = actor.ask(userId, action)
        if (result is MatchResult.Failed) throw ServiceException(result.code, result.message)
        return result
    }
}
