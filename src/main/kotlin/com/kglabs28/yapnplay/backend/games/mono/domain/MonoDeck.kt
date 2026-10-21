package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants

fun buildMonoDeck(): List<MonoCard> {
    val colors = listOf(MonoConstants.Color.RED, MonoConstants.Color.GREEN, MonoConstants.Color.BLUE, MonoConstants.Color.GOLD)
    val cards = mutableListOf<MonoCard>()

    colors.forEach { color -> cards.add(MonoCard.NumberCard(color, 0)) }

    colors.forEach { color ->
        repeat(2) {
            (1..9).forEach { number -> cards.add(MonoCard.NumberCard(color, number)) }
        }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoCard.ReverseCard(color)) }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoCard.SkipCard(color)) }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoCard.DrawTwoCard(color)) }
    }

    repeat(4) { cards.add(MonoCard.PrismColorWild) }
    repeat(4) { cards.add(MonoCard.PrismDrawFour) }

    require(cards.size == 108) { "Standard MONO deck must have 108 cards, got ${cards.size}" }
    return cards
}
