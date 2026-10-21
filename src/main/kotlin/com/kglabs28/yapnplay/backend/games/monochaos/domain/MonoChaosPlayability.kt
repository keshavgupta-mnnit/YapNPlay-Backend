package com.kglabs28.yapnplay.backend.games.monochaos.domain

import com.kglabs28.yapnplay.backend.games.monochaos.identity.MonoChaosConstants

/**
 * Whether [card] may be played on top of [topCard] in [topColor]. Shared
 * by the engine (to reject an illegal move) and the table (to dim the
 * cards the player cannot use right now), so both always agree.
 */
fun MonoChaosCard.isPlayableOn(topCard: MonoChaosCard?, topColor: MonoChaosConstants.Color?): Boolean {
    // A Prism card can be played on anything.
    if (isPrism()) return true
    // Before a colour is chosen, only a Prism card can go down.
    val effective = topColor ?: topCard?.playColor() ?: return false
    val self = this
    return when (self) {
        is MonoChaosCard.NumberCard -> self.color == effective || self.number == (topCard as? MonoChaosCard.NumberCard)?.number
        is MonoChaosCard.ReverseCard -> self.color == effective || topCard is MonoChaosCard.ReverseCard
        is MonoChaosCard.SkipCard -> self.color == effective || topCard is MonoChaosCard.SkipCard
        is MonoChaosCard.DrawTwoCard -> self.color == effective || topCard is MonoChaosCard.DrawTwoCard
        is MonoChaosCard.ColoredDrawFourCard -> self.color == effective || topCard is MonoChaosCard.ColoredDrawFourCard
        is MonoChaosCard.DiscardAllCard -> self.color == effective || topCard is MonoChaosCard.DiscardAllCard
        is MonoChaosCard.SkipEveryoneCard -> self.color == effective || topCard is MonoChaosCard.SkipEveryoneCard
        else -> false
    }
}

/** Whether this card can be stacked onto a penalty of [pending] cards. */
fun MonoChaosCard.canStackOnto(pending: Int): Boolean = penaltyValue() > 0 && penaltyValue() >= pending