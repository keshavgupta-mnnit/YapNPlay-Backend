package com.kglabs28.yapnplay.backend.application.admin

import com.kglabs28.yapnplay.backend.application.common.ServiceException
import com.kglabs28.yapnplay.backend.application.match.MatchRegistry
import com.kglabs28.yapnplay.backend.contract.ErrorCode
import kotlinx.serialization.Serializable

/** Live matches and what their bots decided lately ("why did the bot do that"). Read-only. */
class AdminMatchesUseCase(private val matches: MatchRegistry) {

    fun live(): List<AdminMatchView> = matches.all().filter { !it.isFinished }
        .sortedByDescending { it.startedAt }
        .map { AdminMatchView(it.matchId, it.gameId, it.title, it.roomId, it.startedAt) }

    fun botDecisions(matchId: String): List<AdminBotDecisionView> {
        val actor = matches.find(matchId) ?: throw ServiceException(ErrorCode.NOT_FOUND, "No such match running")
        return actor.botTrace().map { entry ->
            AdminBotDecisionView(
                decision = entry.decision,
                chosen = entry.chosen,
                riskWeight = entry.riskWeight,
                options = entry.options.map { AdminBotOptionView(it.option, it.gain.shown(), it.cost.shown(), it.risk.shown(), it.note) },
            )
        }
    }
}

/** JSON has no infinity: an "always" / "never" option shows as ±1e12. */
private fun Double.shown(): Double = if (isInfinite()) Math.copySign(SHOWN_INFINITY, this) else this

private const val SHOWN_INFINITY = 1e12

@Serializable
data class AdminMatchView(val matchId: String, val gameId: String, val title: String, val roomId: String?, val startedAt: Long)

@Serializable
data class AdminBotDecisionView(val decision: String, val chosen: String, val riskWeight: Double, val options: List<AdminBotOptionView>)

@Serializable
data class AdminBotOptionView(val option: String, val gain: Double, val cost: Double, val risk: Double, val note: String)
