package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class MonoCard {
    @Serializable @SerialName("NumberCard") data class NumberCard(val color: MonoConstants.Color, val number: Int) : MonoCard()
    @Serializable @SerialName("ReverseCard") data class ReverseCard(val color: MonoConstants.Color) : MonoCard()
    @Serializable @SerialName("SkipCard") data class SkipCard(val color: MonoConstants.Color) : MonoCard()
    @Serializable @SerialName("DrawTwoCard") data class DrawTwoCard(val color: MonoConstants.Color) : MonoCard()
    @Serializable @SerialName("PrismColorWild") object PrismColorWild : MonoCard()
    @Serializable @SerialName("PrismDrawFour") object PrismDrawFour : MonoCard()

    fun colorName(): String = when (this) {
        is NumberCard -> when (color) { MonoConstants.Color.RED -> "Red"; MonoConstants.Color.GREEN -> "Green"; MonoConstants.Color.BLUE -> "Blue"; MonoConstants.Color.GOLD -> "Gold" }
        is ReverseCard -> when (color) { MonoConstants.Color.RED -> "Red"; MonoConstants.Color.GREEN -> "Green"; MonoConstants.Color.BLUE -> "Blue"; MonoConstants.Color.GOLD -> "Gold" }
        is SkipCard -> when (color) { MonoConstants.Color.RED -> "Red"; MonoConstants.Color.GREEN -> "Green"; MonoConstants.Color.BLUE -> "Blue"; MonoConstants.Color.GOLD -> "Gold" }
        is DrawTwoCard -> when (color) { MonoConstants.Color.RED -> "Red"; MonoConstants.Color.GREEN -> "Green"; MonoConstants.Color.BLUE -> "Blue"; MonoConstants.Color.GOLD -> "Gold" }
        is PrismColorWild -> "Prism"
        is PrismDrawFour -> "Prism"
    }

    fun playColor(): MonoConstants.Color? = when (this) {
        is NumberCard -> color
        is ReverseCard -> color
        is SkipCard -> color
        is DrawTwoCard -> color
        is PrismColorWild -> null
        is PrismDrawFour -> null
    }

    fun isPrism(): Boolean = this is PrismColorWild || this is PrismDrawFour

    /** Cards this one makes the next player draw; 0 when it is not a draw card. */
    fun penaltyValue(): Int = when (this) {
        is DrawTwoCard -> 2
        is PrismDrawFour -> 4
        else -> 0
    }

    fun glyph(): String = when (this) {
        is NumberCard -> number.toString()
        is ReverseCard -> "⇄"
        is SkipCard -> "⊘"
        is DrawTwoCard -> "+2"
        is PrismColorWild -> "PRISM"
        is PrismDrawFour -> "+4"
    }
}
