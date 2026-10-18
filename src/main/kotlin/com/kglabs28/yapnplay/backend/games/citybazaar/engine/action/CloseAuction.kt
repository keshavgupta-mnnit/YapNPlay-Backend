package com.kglabs28.yapnplay.backend.games.citybazaar.engine.action

import com.kglabs28.yapnplay.backend.games.base.play.Action

/** The auction timer ran out: the highest bidder buys, else the bank does at half price (server). */
data object CloseAuction : Action
