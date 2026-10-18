package com.kglabs28.yapnplay.backend.application.match.games

/** How the server plays a seat. */
enum class BotMode {
    /** CPU seats, Autoplay, away or bot-held seats: the full bot. */
    FULL,

    /** The owner let their turn time run out: the cautious bot (City Bazaar adds the late-roll fine). */
    TIMEOUT,
}
