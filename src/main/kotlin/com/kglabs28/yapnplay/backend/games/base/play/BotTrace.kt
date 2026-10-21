package com.kglabs28.yapnplay.backend.games.base.play

import org.slf4j.LoggerFactory

/**
 * The last [capacity] bot decisions of one match, for "why did the bot do
 * that" on the admin page; each is also logged at debug level. Cheap: a
 * small ring of short strings, nothing is saved.
 */
class BotTrace(private val capacity: Int = DEFAULT_CAPACITY) {
    private val entries = ArrayDeque<BotTraceEntry>()

    @Synchronized
    fun record(decision: String, chosen: String, top: List<Pair<String, Evaluation>>, riskWeight: Double) {
        val entry = BotTraceEntry(
            decision = decision,
            chosen = chosen,
            riskWeight = riskWeight,
            options = top.map { (label, e) -> BotTraceOption(label, e.gain, e.cost, e.risk, e.note) },
        )
        if (log.isDebugEnabled) log.debug("Bot {}: {} ({})", decision, chosen, entry.options)
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    /** Newest first. */
    @Synchronized
    fun recent(): List<BotTraceEntry> = entries.reversed()

    private companion object {
        const val DEFAULT_CAPACITY = 50
        val log = LoggerFactory.getLogger(BotTrace::class.java)
    }
}

data class BotTraceEntry(
    val decision: String,
    val chosen: String,
    val riskWeight: Double,
    val options: List<BotTraceOption>,
)

data class BotTraceOption(
    val option: String,
    val gain: Double,
    val cost: Double,
    val risk: Double,
    val note: String,
)
