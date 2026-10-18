package com.kglabs28.yapnplay.backend.games.base.play

import com.kglabs28.yapnplay.backend.games.base.identity.SeatNumber
import com.kglabs28.yapnplay.backend.games.base.match.GameState

/**
 * A computer player. Every game ships one: it plays CPU seats, seats
 * whose time ran out and anyone who turned autoplay on. It may only pick
 * a legal action, and should pick the best one it can see. Pure, like
 * the engine: same state in, same action out.
 */
interface GameBot {
    /** What [seat] should do now, or null when it has nothing to do. */
    fun chooseAction(state: GameState, seat: SeatNumber): Action?
}
