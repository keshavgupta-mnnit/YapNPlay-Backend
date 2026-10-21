package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants

fun buildMonoChaosDeck(): List<MonoChaosCard> {
    val colors = listOf(MonoChaosConstants.Color.RED, MonoChaosConstants.Color.GREEN, MonoChaosConstants.Color.BLUE, MonoChaosConstants.Color.GOLD)
    val cards = mutableListOf<MonoChaosCard>()

    colors.forEach { color ->
        repeat(2) {
            (0..9).forEach { number -> cards.add(MonoChaosCard.NumberCard(color, number)) }
        }
    }

    colors.forEach { color ->
        repeat(3) { cards.add(MonoChaosCard.ReverseCard(color)) }
    }

    colors.forEach { color ->
        repeat(3) { cards.add(MonoChaosCard.SkipCard(color)) }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoChaosCard.DrawTwoCard(color)) }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoChaosCard.ColoredDrawFourCard(color)) }
    }

    colors.forEach { color ->
        repeat(3) { cards.add(MonoChaosCard.DiscardAllCard(color)) }
    }

    colors.forEach { color ->
        repeat(2) { cards.add(MonoChaosCard.SkipEveryoneCard(color)) }
    }

    repeat(8) { cards.add(MonoChaosCard.PrismReverseDrawFour) }
    repeat(4) { cards.add(MonoChaosCard.PrismDrawSix) }
    repeat(4) { cards.add(MonoChaosCard.PrismDrawTen) }
    repeat(8) { cards.add(MonoChaosCard.PrismColorRoulette) }

    colors.forEach { color -> cards.add(MonoChaosCard.DrawTwoCard(color)) }

    require(cards.size == 168) { "MONO: Complete Chaos deck must have 168 cards, got ${cards.size}" }
    return cards
}
