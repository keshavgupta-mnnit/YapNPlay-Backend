package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** Build a house (or the hotel) on [tile]. */
data class BuildOnTile(val tile: Int) : Action
