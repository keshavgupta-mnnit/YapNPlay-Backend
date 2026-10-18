package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** Sell [tile] (no buildings) to the bank at half its price. */
data class SellTile(val tile: Int) : Action
