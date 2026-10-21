package com.kglabs28.yapnplay.backend.games.mono.domain

import com.kglabs28.yapnplay.backend.games.mono.identity.MonoConstants

/**
 * Whether [card] may be played on top of [topCard] in [topColor]. Shared
 * by the engine (to reject an illegal move) and the table (to dim the
 * cards the player cannot use right now), so both always agree.
 */
fun MonoCard.isPlayableOn(topCard: MonoCard?, topColor: MonoConstants.Color?): Boolean {
    // A Prism card can be played on anything.
    if (isPrism()) return true
    // Before a colour is chosen, only a Prism card can go down.
    val effective = topColor ?: topCard?.playColor() ?: return false
    val self = this
    return when (self) {
        is MonoCard.NumberCard -> self.color == effective || self.number == (topCard as? MonoCard.NumberCard)?.number
        is MonoCard.ReverseCard -> self.color == effective || topCard is MonoCard.ReverseCard
        is MonoCard.SkipCard -> self.color == effective || topCard is MonoCard.SkipCard
        is MonoCard.DrawTwoCard -> self.color == effective || topCard is MonoCard.DrawTwoCard
        else -> false
    }
}