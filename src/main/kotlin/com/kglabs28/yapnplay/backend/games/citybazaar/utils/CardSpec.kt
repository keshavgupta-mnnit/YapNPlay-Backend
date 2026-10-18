package com.kglabs28.yapnplay.backend.games.citybazaar.utils

/** One result of a Luck or Shagun Chest table: the dice total [roll] (2–12) that picks it, its text and effect. */
data class CardSpec(val id: String, val deck: CardDeck, val roll: Int, val text: String, val effect: CardEffect)
