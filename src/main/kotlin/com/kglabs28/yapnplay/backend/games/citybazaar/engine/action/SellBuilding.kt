package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** Sell one building on [tile] to the bank at half its build cost. */
data class SellBuilding(val tile: Int) : Action
