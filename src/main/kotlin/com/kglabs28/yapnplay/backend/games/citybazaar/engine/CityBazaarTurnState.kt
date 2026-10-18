package com.kglabs28.yapnplay.backend.games.citybazaar.engine

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.TurnState

data class CityBazaarTurnState(override val seat: SeatNumber, override val phase: String) : TurnState
