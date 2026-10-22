package com.kglabs28.yapnplay.backend.application.match.games

import kotlin.random.Random

/**
 * How long a seat the server plays waits before its move, so bots look
 * like people thinking: [baseMs] plus up to [jitterMs] at random, plus
 * [decisionExtraMs] for a real choice (a colour, a purchase, which piece).
 * The match actor never lets the wait run past the turn's deadline.
 */
data class BotThinkTime(
    val baseMs: Long,
    val jitterMs: Long,
    val decisionExtraMs: Long,
) {
    fun millis(decision: Boolean): Long =
        baseMs + (if (decision) decisionExtraMs else 0L) + if (jitterMs > 0) Random.nextLong(jitterMs + 1) else 0L
}
