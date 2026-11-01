package com.kglabs28.yapnplay.backend.application.match.games.doodleking

import com.kglabs28.yapnplay.backend.contract.doodleking.DoodleStroke
import com.kglabs28.yapnplay.backend.games.base.play.Action
import com.kglabs28.yapnplay.backend.games.base.play.GameRandom
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleGuess
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingState
import com.kglabs28.yapnplay.backend.games.doodleking.DoodleKingWords
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePhase
import com.kglabs28.yapnplay.backend.games.doodleking.DoodlePick

/**
 * TESTING ONLY (admin setting `dk.test_bots`), to be deleted: CPU seats so
 * a table can be tried alone. They pick the first word, "draw" a fixed
 * sketch and guess at random, more often as time and hints go by. Not a
 * real bot: Doodle King is played by people.
 */
class DoodleTestBots(private val random: GameRandom) {

    /** What one bot seat does on this tick ([seat], its action), or null. */
    fun act(s: DoodleKingState, bots: Set<Int>, now: Long): Pair<Int, Action>? {
        if (s.drawer in bots && s.phase == DoodlePhase.PICK && now >= s.phaseEndsAt - s.dkRules.mandatory.pickMs + PICK_DELAY_MS) {
            return s.drawer to DoodlePick(0, now)
        }
        if (s.phase != DoodlePhase.DRAW) return null
        val word = s.word ?: return null
        val elapsed = (now - s.drawStartedAt).toDouble() / s.dkRules.mandatory.drawMs
        val seat = bots.filter { it != s.drawer && it !in s.guessed && it !in s.removed }.randomOrNull() ?: return null
        val roll = random.nextInt(PERCENT)
        val rightChance = (RIGHT_BASE + RIGHT_PER_TIME * elapsed + RIGHT_PER_HINT * s.hintsShown).toInt()
        return when {
            roll < rightChance -> seat to DoodleGuess(word, now)
            roll < rightChance + WRONG_CHANCE -> seat to DoodleGuess(DoodleKingWords.pool(s.level, false).let { it[random.nextInt(it.size)] }, now)
            else -> null
        }
    }

    /** The sketch a bot drawer leaves on the canvas: a little house. */
    fun sketch(drawingId: Int): List<DoodleStroke> = HOUSE.mapIndexed { i, points -> DoodleStroke(drawingId, i + 1, points = points) }

    private fun <T> List<T>.randomOrNull(): T? = if (isEmpty()) null else this[random.nextInt(size)]

    private companion object {
        const val PICK_DELAY_MS = 2_000L
        const val PERCENT = 100
        const val RIGHT_BASE = 1.0
        const val RIGHT_PER_TIME = 8.0
        const val RIGHT_PER_HINT = 3.0
        const val WRONG_CHANCE = 6
        val HOUSE = listOf(
            listOf(1200, 1800, 1200, 3400, 2900, 3400, 2900, 1800, 1200, 1800),
            listOf(1000, 1900, 2050, 900, 3100, 1900),
            listOf(1850, 3400, 1850, 2600, 2250, 2600, 2250, 3400),
            listOf(2500, 2100, 2750, 2100, 2750, 2400, 2500, 2400, 2500, 2100),
        )
    }
}
