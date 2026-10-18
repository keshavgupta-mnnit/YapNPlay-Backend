package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** Put [tile] (no buildings) up for auction among the other players, to raise money for a debt. */
data class StartAuction(val tile: Int) : Action
