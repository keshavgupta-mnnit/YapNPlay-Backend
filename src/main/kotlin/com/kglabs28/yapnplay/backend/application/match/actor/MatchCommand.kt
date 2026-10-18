package com.kglabs28.yapnplay.backend.application.match.actor

import kotlinx.coroutines.CompletableDeferred

/** One item in a match actor's inbox. Small hierarchy, kept together. */
sealed interface MatchCommand {
    data class Request(
        val userId: String,
        val action: MatchAction,
        val reply: CompletableDeferred<MatchResult>,
    ) : MatchCommand

    /** Time for the seat whose turn it is to be played; stale when [token] is old. */
    data class Wake(val token: Long) : MatchCommand

    /** The regular clock: presence, handover timeouts, the game's own timers. */
    data object Tick : MatchCommand
}
