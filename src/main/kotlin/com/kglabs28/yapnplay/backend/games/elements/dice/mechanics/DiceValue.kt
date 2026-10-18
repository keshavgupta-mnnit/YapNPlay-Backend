package com.kglabs28.yapnplay.backend.games.elements.dice.mechanics

import kotlinx.serialization.Serializable

/**
 * The face value shown by a die after a roll. Always 1 or more;
 * a standard die has 6 sides (see [DiceRoller]).
 */
@Serializable
@JvmInline
value class DiceValue(val value: Int) {
    init {
        require(value >= 1) { "DiceValue must be >= 1, was $value" }
    }
}
