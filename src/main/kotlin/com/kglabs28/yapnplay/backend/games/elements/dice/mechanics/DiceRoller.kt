package com.kglabs28.yapnplay.backend.games.elements.dice.mechanics

import com.kglabs28.yapnplay.backend.games.base.play.GameRandom

/**
 * Rolls dice for any dice game. Randomness arrives through [GameRandom],
 * so production rolls vary while tests fix the sequence.
 */
object DiceRoller {

    /** Standard six-sided die. */
    const val STANDARD_SIDES = 6

    /**
     * Rolls a die with [sides] sides and returns the face value (1..sides).
     */
    fun roll(random: GameRandom, sides: Int = STANDARD_SIDES): DiceValue {
        require(sides >= 2) { "A die needs at least 2 sides, was $sides" }
        return DiceValue(random.nextInt(sides) + 1)
    }
}
